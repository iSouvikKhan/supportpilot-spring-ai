package com.supportpilot.agent;

import java.math.BigDecimal;
import java.util.UUID;

/** Something the agent did through a tool during one chat turn, shown as a card in the UI. */
public record AgentAction(
        Type type, String orderId, UUID refundRequestId, String status, BigDecimal amount, String message) {

    public enum Type {
        ORDER_LOOKUP,
        ORDERS_LISTED,
        REFUND_REQUESTED,
        REFUND_NOT_ALLOWED,
        REFUND_STATUS_CHECKED
    }
}
