package com.supportpilot.order;

import com.supportpilot.common.NotFoundException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    /**
     * Returns the order only if it belongs to {@code customerEmail}. Orders of other customers
     * are reported as "not found" so their existence is not revealed.
     */
    public Order getForCustomer(String orderId, String customerEmail) {
        String id = normalizeId(orderId);
        return orders.findById(id)
                .filter(o -> o.belongsTo(customerEmail))
                .orElseThrow(() -> new NotFoundException("No order " + id + " found for this customer"));
    }

    public List<Order> listForCustomer(String customerEmail) {
        return orders.findByCustomerEmailIgnoreCaseOrderByOrderedAtDesc(customerEmail.trim());
    }

    public List<OrderRepository.CustomerView> customers() {
        return orders.findCustomers();
    }

    /** Accepts "ord-1001", " ORD-1001 " or "1001". */
    static String normalizeId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new NotFoundException("An order id is required");
        }
        String id = orderId.trim().toUpperCase(Locale.ROOT).replace("#", "");
        return id.matches("\\d+") ? "ORD-" + id : id;
    }
}
