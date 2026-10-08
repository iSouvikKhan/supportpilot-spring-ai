package com.supportpilot.agent;

import static com.supportpilot.TestData.ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supportpilot.TestData;
import com.supportpilot.common.BusinessRuleException;
import com.supportpilot.common.NotFoundException;
import com.supportpilot.order.OrderService;
import com.supportpilot.order.OrderStatus;
import com.supportpilot.refund.RefundRequest;
import com.supportpilot.refund.RefundService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;

class SupportToolsTest {

    private final OrderService orders = mock(OrderService.class);
    private final RefundService refunds = mock(RefundService.class);
    private final SupportTools tools = new SupportTools(orders, refunds);
    private final AgentActions actions = new AgentActions();
    private final ToolContext context = new ToolContext(Map.of(
            SupportTools.CUSTOMER_EMAIL, ALICE,
            SupportTools.CONVERSATION_ID, "conv-123",
            SupportTools.ACTIONS, actions));

    @Test
    void trackOrderUsesCustomerFromToolContext() {
        when(orders.getForCustomer("ORD-1", ALICE)).thenReturn(TestData.order("ORD-1", OrderStatus.DELIVERED, 3));

        SupportTools.OrderLookupResult result = tools.trackOrder("ORD-1", context);

        assertThat(result.found()).isTrue();
        assertThat(result.order().id()).isEqualTo("ORD-1");
        assertThat(actions.list()).extracting(AgentAction::type).containsExactly(AgentAction.Type.ORDER_LOOKUP);
    }

    @Test
    void trackOrderReportsNotFoundAsResult() {
        when(orders.getForCustomer("ORD-9", ALICE)).thenThrow(new NotFoundException("No order ORD-9"));
        SupportTools.OrderLookupResult result = tools.trackOrder("ORD-9", context);
        assertThat(result.found()).isFalse();
        assertThat(result.message()).contains("No order ORD-9");
    }

    @Test
    void requestRefundCreatesPendingRequestOnly() {
        RefundRequest pending = RefundRequest.pending("ORD-1", ALICE, new BigDecimal("129.99"), "broken", "conv-123",
                TestData.NOW);
        when(refunds.requestRefund("ORD-1", ALICE, null, "broken", "conv-123")).thenReturn(pending);

        SupportTools.RefundToolResult result = tools.requestRefund("ORD-1", "broken", null, context);

        assertThat(result.submitted()).isTrue();
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.message()).contains("human");
        assertThat(actions.list().getFirst().type()).isEqualTo(AgentAction.Type.REFUND_REQUESTED);
        verify(refunds).requestRefund(eq("ORD-1"), eq(ALICE), any(), eq("broken"), eq("conv-123"));
    }

    @Test
    void requestRefundReportsPolicyViolations() {
        when(refunds.requestRefund(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessRuleException("outside the refund window"));

        SupportTools.RefundToolResult result = tools.requestRefund("ORD-1", "late", null, context);

        assertThat(result.submitted()).isFalse();
        assertThat(result.message()).contains("refund window");
        assertThat(actions.list().getFirst().type()).isEqualTo(AgentAction.Type.REFUND_NOT_ALLOWED);
    }

    @Test
    void toolsRefuseToRunWithoutCustomer() {
        assertThatThrownBy(() -> tools.listMyOrders(new ToolContext(Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toolDefinitionsHideTheToolContextFromTheModel() {
        ToolCallback[] callbacks = ToolCallbacks.from(tools);
        assertThat(callbacks).extracting(c -> c.getToolDefinition().name())
                .containsExactlyInAnyOrder("trackOrder", "listMyOrders", "requestRefund", "myRefundRequests");
        for (ToolCallback c : callbacks) {
            String schema = c.getToolDefinition().inputSchema();
            assertThat(schema).doesNotContain("toolContext").doesNotContain("customerEmail");
        }
        String refundSchema = List.of(callbacks).stream()
                .filter(c -> c.getToolDefinition().name().equals("requestRefund"))
                .findFirst().orElseThrow().getToolDefinition().inputSchema();
        assertThat(refundSchema).contains("orderId").contains("reason").contains("amount");
    }
}
