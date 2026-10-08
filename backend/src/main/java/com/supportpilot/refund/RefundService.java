package com.supportpilot.refund;

import com.supportpilot.common.BusinessRuleException;
import com.supportpilot.common.NotFoundException;
import com.supportpilot.config.SupportPilotProperties;
import com.supportpilot.order.Order;
import com.supportpilot.order.OrderRepository;
import com.supportpilot.order.OrderService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refund workflow. The AI agent can only call {@link #requestRefund}, which creates a PENDING
 * request. {@link #approve} and {@link #reject} are reachable only from the approver API.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    static final int MAX_REASON_LENGTH = 1000;

    private final RefundRepository refunds;
    private final OrderRepository orderRepository;
    private final OrderService orders;
    private final Clock clock;
    private final int windowDays;

    public RefundService(RefundRepository refunds, OrderRepository orderRepository, OrderService orders,
                         Clock clock, SupportPilotProperties properties) {
        this.refunds = refunds;
        this.orderRepository = orderRepository;
        this.orders = orders;
        this.clock = clock;
        this.windowDays = properties.refunds().windowDays();
    }

    /**
     * Creates a PENDING refund request after checking the refund policy.
     *
     * @param amount amount to refund, or {@code null} for the full refundable balance
     * @throws NotFoundException if the order does not exist or belongs to someone else
     * @throws BusinessRuleException if the policy does not allow the refund
     */
    @Transactional
    public RefundRequest requestRefund(String orderId, String customerEmail, BigDecimal amount,
                                       String reason, String conversationId) {
        Order order = orders.getForCustomer(orderId, customerEmail);
        String cleanReason = reason == null ? "" : reason.strip();
        if (cleanReason.isEmpty()) {
            throw new BusinessRuleException("A reason for the refund is required");
        }
        if (cleanReason.length() > MAX_REASON_LENGTH) {
            cleanReason = cleanReason.substring(0, MAX_REASON_LENGTH);
        }

        switch (order.getStatus()) {
            case DELIVERED -> { }
            case PROCESSING, SHIPPED -> throw new BusinessRuleException(
                    "Order " + order.getId() + " is " + order.getStatus().name().toLowerCase()
                            + "; refunds can be requested once it has been delivered");
            case CANCELLED -> throw new BusinessRuleException(
                    "Order " + order.getId() + " was cancelled and was never charged");
            case REFUNDED -> throw new BusinessRuleException(
                    "Order " + order.getId() + " has already been fully refunded");
        }

        Instant now = clock.instant();
        if (order.getDeliveredAt() != null
                && Duration.between(order.getDeliveredAt(), now).toDays() > windowDays) {
            throw new BusinessRuleException("Order " + order.getId() + " was delivered more than "
                    + windowDays + " days ago, which is outside the refund window");
        }
        if (refunds.existsByOrderIdAndStatus(order.getId(), RefundStatus.PENDING)) {
            throw new BusinessRuleException(
                    "There is already a pending refund request for order " + order.getId());
        }

        BigDecimal balance = order.refundableBalance();
        BigDecimal value = amount == null ? balance : amount.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() <= 0) {
            throw new BusinessRuleException("The refund amount must be greater than zero");
        }
        if (value.compareTo(balance) > 0) {
            throw new BusinessRuleException("The refund amount " + value + " exceeds the refundable balance of "
                    + balance + " " + order.getCurrency());
        }

        RefundRequest request;
        try {
            // Flush now so the "one pending request per order" unique index is checked here.
            request = refunds.saveAndFlush(RefundRequest.pending(
                    order.getId(), customerEmail, value, cleanReason, conversationId, now));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException(
                    "There is already a pending refund request for order " + order.getId());
        }
        log.info("Refund request {} created for order {} ({} {})", request.getId(), order.getId(),
                value, order.getCurrency());
        return request;
    }

    @Transactional
    public RefundRequest approve(UUID id, String approver, String note) {
        RefundRequest request = pending(id);
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new NotFoundException("Order " + request.getOrderId() + " not found"));
        if (request.getAmount().compareTo(order.refundableBalance()) > 0) {
            throw new BusinessRuleException("The order no longer has enough refundable balance");
        }
        order.applyRefund(request.getAmount());
        request.decide(RefundStatus.APPROVED, approver, note, clock.instant());
        log.info("Refund request {} approved by {}", id, approver);
        return request;
    }

    @Transactional
    public RefundRequest reject(UUID id, String approver, String note) {
        RefundRequest request = pending(id);
        request.decide(RefundStatus.REJECTED, approver, note, clock.instant());
        log.info("Refund request {} rejected by {}", id, approver);
        return request;
    }

    @Transactional(readOnly = true)
    public List<RefundRequest> list(RefundStatus status) {
        return status == null
                ? refunds.findAllByOrderByCreatedAtDesc()
                : refunds.findByStatusOrderByCreatedAtAsc(status);
    }

    @Transactional(readOnly = true)
    public List<RefundRequest> listForCustomer(String email) {
        return refunds.findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(email.trim());
    }

    private RefundRequest pending(UUID id) {
        RefundRequest request = refunds.findById(id)
                .orElseThrow(() -> new NotFoundException("Refund request " + id + " not found"));
        if (request.getStatus() != RefundStatus.PENDING) {
            throw new BusinessRuleException("Refund request is already " + request.getStatus());
        }
        return request;
    }
}
