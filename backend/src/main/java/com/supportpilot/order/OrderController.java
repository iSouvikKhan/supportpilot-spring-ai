package com.supportpilot.order;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing order endpoints. The demo has no login: the UI picks one of the sample
 * customers and passes their email (see README, "Security notes").
 */
@RestController
@RequestMapping("/api")
@Validated
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    public record CustomerDto(String email, String name) {}

    @GetMapping("/customers")
    public List<CustomerDto> customers() {
        return orders.customers().stream().map(c -> new CustomerDto(c.getEmail(), c.getName())).toList();
    }

    @GetMapping("/orders")
    public List<OrderDto> list(@RequestParam @Email @NotBlank String email) {
        return orders.listForCustomer(email).stream().map(OrderDto::from).toList();
    }

    @GetMapping("/orders/{id}")
    public OrderDto get(@PathVariable String id, @RequestParam @Email @NotBlank String email) {
        return OrderDto.from(orders.getForCustomer(id, email));
    }
}
