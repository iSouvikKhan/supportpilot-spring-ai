package com.supportpilot.refund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundDto(
        UUID id,
        String orderId,
        String customerEmail,
        BigDecimal amount,
        String reason,
        RefundStatus status,
        Instant createdAt,
        Instant decidedAt,
        String decidedBy,
        String decisionNote) {

    public static RefundDto from(RefundRequest r) {
        return new RefundDto(
                r.getId(),
                r.getOrderId(),
                r.getCustomerEmail(),
                r.getAmount(),
                r.getReason(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getDecidedAt(),
                r.getDecidedBy(),
                r.getDecisionNote());
    }
}
