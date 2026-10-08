package com.supportpilot.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Order details exposed to the UI and to the AI agent's tools. */
public record OrderDto(
        String id,
        String customerName,
        OrderStatus status,
        BigDecimal total,
        BigDecimal refundedAmount,
        String currency,
        String carrier,
        String trackingNumber,
        Instant orderedAt,
        Instant shippedAt,
        Instant deliveredAt,
        LocalDate estimatedDelivery,
        List<Item> items) {

    public record Item(String productName, int quantity, BigDecimal unitPrice) {}

    public static OrderDto from(Order o) {
        return new OrderDto(
                o.getId(),
                o.getCustomerName(),
                o.getStatus(),
                o.getTotal(),
                o.getRefundedAmount(),
                o.getCurrency(),
                o.getCarrier(),
                o.getTrackingNumber(),
                o.getOrderedAt(),
                o.getShippedAt(),
                o.getDeliveredAt(),
                o.getEstimatedDelivery(),
                o.getItems().stream()
                        .map(i -> new Item(i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                        .toList());
    }
}
