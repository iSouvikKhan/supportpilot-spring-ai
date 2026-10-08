package com.supportpilot;

import com.supportpilot.config.SupportPilotProperties;
import com.supportpilot.order.Order;
import com.supportpilot.order.OrderItem;
import com.supportpilot.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class TestData {

    public static final Instant NOW = Instant.parse("2026-03-15T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final String ALICE = "alice@example.com";

    private TestData() {}

    public static SupportPilotProperties properties() {
        return new SupportPilotProperties(
                new SupportPilotProperties.Approver("test-key"),
                new SupportPilotProperties.Rag(4, 0.4),
                new SupportPilotProperties.Refunds(30),
                new SupportPilotProperties.Knowledge("classpath:knowledge/*.md", false),
                List.of("http://localhost:4200"));
    }

    public static Order order(String id, OrderStatus status, Integer deliveredDaysAgo, String... prices) {
        List<OrderItem> items = prices.length == 0
                ? List.of(new OrderItem("Earbuds", 1, new BigDecimal("129.99")))
                : java.util.Arrays.stream(prices).map(p -> new OrderItem("Item", 1, new BigDecimal(p))).toList();
        Order order = new Order(id, ALICE, "Alice Johnson", status, "USD", NOW.minus(Duration.ofDays(40)), items);
        if (deliveredDaysAgo != null) {
            order.markDelivered(NOW.minus(Duration.ofDays(deliveredDaysAgo)));
        }
        return order;
    }
}
