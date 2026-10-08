package com.supportpilot.order;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 64)
    private String carrier;

    @Column(name = "tracking_number", length = 64)
    private String trackingNumber;

    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "estimated_delivery")
    private LocalDate estimatedDelivery;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "position")
    private List<OrderItem> items = new ArrayList<>();

    @Version
    private long version;

    protected Order() {}

    public Order(String id, String customerEmail, String customerName, OrderStatus status,
                 String currency, Instant orderedAt, List<OrderItem> items) {
        this.id = id;
        this.customerEmail = customerEmail.toLowerCase();
        this.customerName = customerName;
        this.status = status;
        this.currency = currency;
        this.orderedAt = orderedAt;
        this.items = new ArrayList<>(items);
        this.total = items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Amount that can still be refunded. */
    public BigDecimal refundableBalance() {
        return total.subtract(refundedAmount);
    }

    public boolean belongsTo(String email) {
        return email != null && customerEmail.equalsIgnoreCase(email.trim());
    }

    /** Records an approved refund; a fully refunded order becomes REFUNDED. */
    public void applyRefund(BigDecimal amount) {
        if (amount.signum() <= 0 || amount.compareTo(refundableBalance()) > 0) {
            throw new IllegalArgumentException("Refund amount exceeds the refundable balance");
        }
        refundedAmount = refundedAmount.add(amount);
        if (refundableBalance().signum() == 0) {
            status = OrderStatus.REFUNDED;
        }
    }

    public void markShipped(String carrier, String trackingNumber, Instant shippedAt, LocalDate eta) {
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.shippedAt = shippedAt;
        this.estimatedDelivery = eta;
    }

    public void markDelivered(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getId() {
        return id;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getCustomerName() {
        return customerName;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public Instant getOrderedAt() {
        return orderedAt;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public LocalDate getEstimatedDelivery() {
        return estimatedDelivery;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }
}
