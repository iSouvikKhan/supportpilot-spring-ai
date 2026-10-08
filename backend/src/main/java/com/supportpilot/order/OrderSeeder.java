package com.supportpilot.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads sample orders from {@code seed/orders.json} when the orders table is empty.
 * Dates in the file are relative ("delivered 5 days ago"), so refund-window scenarios behave
 * the same whenever the demo is started.
 */
@Component
public class OrderSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderSeeder.class);
    static final String SEED_FILE = "seed/orders.json";

    private final OrderRepository orders;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OrderSeeder(OrderRepository orders, ObjectMapper objectMapper, Clock clock) {
        this.orders = orders;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    record SeedItem(String product, int quantity, BigDecimal unitPrice) {}

    record SeedOrder(String id, String customerEmail, String customerName, OrderStatus status,
                     String currency, int orderedDaysAgo, Integer shippedDaysAgo, Integer deliveredDaysAgo,
                     Integer etaInDays, String carrier, String trackingNumber, List<SeedItem> items) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (orders.count() > 0) {
            log.info("Orders already present, skipping seed data");
            return;
        }
        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            SeedOrder[] seed = objectMapper.readValue(in, SeedOrder[].class);
            Instant now = clock.instant();
            for (SeedOrder s : seed) {
                orders.save(toOrder(s, now));
            }
            log.info("Seeded {} sample orders", seed.length);
        }
    }

    static Order toOrder(SeedOrder s, Instant now) {
        List<OrderItem> items = s.items().stream()
                .map(i -> new OrderItem(i.product(), i.quantity(), i.unitPrice()))
                .toList();
        Order order = new Order(s.id(), s.customerEmail(), s.customerName(), s.status(), s.currency(),
                daysAgo(now, s.orderedDaysAgo()), items);
        if (s.shippedDaysAgo() != null) {
            LocalDate eta = s.etaInDays() == null
                    ? null
                    : LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(s.etaInDays());
            order.markShipped(s.carrier(), s.trackingNumber(), daysAgo(now, s.shippedDaysAgo()), eta);
        }
        if (s.deliveredDaysAgo() != null) {
            order.markDelivered(daysAgo(now, s.deliveredDaysAgo()));
        }
        return order;
    }

    private static Instant daysAgo(Instant now, int days) {
        return now.minus(Duration.ofDays(days));
    }
}
