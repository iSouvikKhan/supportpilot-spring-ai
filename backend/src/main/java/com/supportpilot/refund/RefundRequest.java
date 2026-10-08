package com.supportpilot.refund;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A refund the AI agent asked for on behalf of a customer. It only ever starts as PENDING;
 * money moves when a human approver approves it.
 */
@Entity
@Table(name = "refund_requests")
public class RefundRequest {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, length = 32)
    private String orderId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Version
    private long version;

    protected RefundRequest() {}

    public static RefundRequest pending(String orderId, String customerEmail, BigDecimal amount,
                                        String reason, String conversationId, Instant now) {
        RefundRequest r = new RefundRequest();
        r.id = UUID.randomUUID();
        r.orderId = orderId;
        r.customerEmail = customerEmail.toLowerCase();
        r.amount = amount;
        r.reason = reason;
        r.conversationId = conversationId;
        r.status = RefundStatus.PENDING;
        r.createdAt = now;
        return r;
    }

    void decide(RefundStatus decision, String approver, String note, Instant now) {
        if (status != RefundStatus.PENDING) {
            throw new IllegalStateException("Refund request is already " + status);
        }
        if (decision == RefundStatus.PENDING) {
            throw new IllegalArgumentException("A decision must be APPROVED or REJECTED");
        }
        this.status = decision;
        this.decidedBy = approver;
        this.decisionNote = note;
        this.decidedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getConversationId() {
        return conversationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }
}
