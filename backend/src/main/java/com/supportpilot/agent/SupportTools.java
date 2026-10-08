package com.supportpilot.agent;

import com.supportpilot.common.BusinessRuleException;
import com.supportpilot.common.NotFoundException;
import com.supportpilot.order.OrderDto;
import com.supportpilot.order.OrderService;
import com.supportpilot.refund.RefundDto;
import com.supportpilot.refund.RefundRequest;
import com.supportpilot.refund.RefundService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Tools the language model can call.
 *
 * <p>The customer's identity comes from the {@link ToolContext}, which the server fills in and
 * the model never sees or controls. A prompt such as "look up bob@example.com's orders" cannot
 * reach another customer's data. Tools report problems as results instead of throwing, so the
 * model can explain them to the customer.
 */
@Component
public class SupportTools {

    public static final String CUSTOMER_EMAIL = "customerEmail";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String ACTIONS = "actions";

    private static final Logger log = LoggerFactory.getLogger(SupportTools.class);

    private final OrderService orders;
    private final RefundService refunds;

    public SupportTools(OrderService orders, RefundService refunds) {
        this.orders = orders;
        this.refunds = refunds;
    }

    public record OrderLookupResult(boolean found, String message, OrderDto order) {}

    public record RefundToolResult(
            boolean submitted, String message, UUID refundRequestId, String status, BigDecimal amount) {}

    @Tool(description = """
            Look up one of the signed-in customer's orders: status, items, total, carrier, \
            tracking number, ship/delivery dates and estimated delivery. Use it whenever the \
            customer asks where an order is or about its details.""")
    public OrderLookupResult trackOrder(
            @ToolParam(description = "The order id, for example ORD-1001") String orderId,
            ToolContext context) {
        String email = customerEmail(context);
        try {
            OrderDto order = OrderDto.from(orders.getForCustomer(orderId, email));
            record(context, new AgentAction(AgentAction.Type.ORDER_LOOKUP, order.id(), null,
                    order.status().name(), order.total(), "Looked up order " + order.id()));
            return new OrderLookupResult(true, "Order found", order);
        } catch (NotFoundException e) {
            log.debug("trackOrder({}) for {}: {}", orderId, email, e.getMessage());
            return new OrderLookupResult(false, e.getMessage(), null);
        }
    }

    @Tool(description = """
            List the signed-in customer's orders (newest first) with id, status, total and \
            dates. Use it when the customer does not know their order id.""")
    public List<OrderDto> listMyOrders(ToolContext context) {
        List<OrderDto> result = orders.listForCustomer(customerEmail(context)).stream()
                .map(OrderDto::from)
                .toList();
        record(context, new AgentAction(AgentAction.Type.ORDERS_LISTED, null, null, null, null,
                "Listed " + result.size() + " orders"));
        return result;
    }

    @Tool(description = """
            Submit a refund request for one of the signed-in customer's DELIVERED orders. \
            This does NOT refund money: it creates a request with status PENDING that a human \
            support agent must approve or reject. Only call it after the customer has confirmed \
            the order id and given a reason. Omit amount to request the full refundable amount.""")
    public RefundToolResult requestRefund(
            @ToolParam(description = "The order id, for example ORD-1001") String orderId,
            @ToolParam(description = "The customer's reason for the refund, in their words") String reason,
            @ToolParam(description = "Amount to refund in the order currency. Omit for the full "
                    + "refundable amount.", required = false) BigDecimal amount,
            ToolContext context) {
        String email = customerEmail(context);
        String conversationId = (String) context.getContext().get(CONVERSATION_ID);
        try {
            RefundRequest request = refunds.requestRefund(orderId, email, amount, reason, conversationId);
            record(context, new AgentAction(AgentAction.Type.REFUND_REQUESTED, request.getOrderId(),
                    request.getId(), request.getStatus().name(), request.getAmount(),
                    "Refund request submitted for human approval"));
            return new RefundToolResult(true,
                    "Refund request submitted. A human support agent will review it; the customer is "
                            + "not refunded until it is approved.",
                    request.getId(), request.getStatus().name(), request.getAmount());
        } catch (NotFoundException | BusinessRuleException e) {
            record(context, new AgentAction(AgentAction.Type.REFUND_NOT_ALLOWED, orderId, null, null,
                    amount, e.getMessage()));
            return new RefundToolResult(false, e.getMessage(), null, null, null);
        }
    }

    @Tool(description = """
            Get the status (PENDING, APPROVED or REJECTED) of the signed-in customer's refund \
            requests, including any note from the reviewer.""")
    public List<RefundDto> myRefundRequests(ToolContext context) {
        List<RefundDto> result = refunds.listForCustomer(customerEmail(context)).stream()
                .map(RefundDto::from)
                .toList();
        record(context, new AgentAction(AgentAction.Type.REFUND_STATUS_CHECKED, null, null, null, null,
                "Checked " + result.size() + " refund requests"));
        return result;
    }

    private static String customerEmail(ToolContext context) {
        Object email = context == null ? null : context.getContext().get(CUSTOMER_EMAIL);
        if (!(email instanceof String s) || s.isBlank()) {
            throw new IllegalStateException("Tool called without a customer in the ToolContext");
        }
        return s;
    }

    private static void record(ToolContext context, AgentAction action) {
        if (context.getContext().get(ACTIONS) instanceof AgentActions actions) {
            actions.add(action);
        }
    }
}
