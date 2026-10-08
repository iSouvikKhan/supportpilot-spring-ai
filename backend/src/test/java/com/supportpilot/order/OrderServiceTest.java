package com.supportpilot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.supportpilot.TestData;
import com.supportpilot.common.NotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderServiceTest {

    private final OrderRepository repository = mock(OrderRepository.class);
    private final OrderService service = new OrderService(repository);

    @ParameterizedTest
    @CsvSource({"ORD-1001,ORD-1001", "ord-1001,ORD-1001", "' #1001 ',ORD-1001", "1001,ORD-1001"})
    void normalizesOrderIds(String input, String expected) {
        assertThat(OrderService.normalizeId(input)).isEqualTo(expected);
    }

    @Test
    void returnsOwnOrderCaseInsensitively() {
        Order order = TestData.order("ORD-1", OrderStatus.DELIVERED, 2);
        when(repository.findById("ORD-1")).thenReturn(Optional.of(order));
        assertThat(service.getForCustomer("ord-1", "Alice@Example.com")).isSameAs(order);
    }

    @Test
    void hidesOtherCustomersOrders() {
        Order order = TestData.order("ORD-1", OrderStatus.DELIVERED, 2);
        when(repository.findById("ORD-1")).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.getForCustomer("ORD-1", "bob@example.com"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void blankIdIsRejected() {
        assertThatThrownBy(() -> service.getForCustomer(" ", "a@b.c")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void orderTotalsAndRefundRules() {
        Order order = TestData.order("ORD-1", OrderStatus.DELIVERED, 2, "10.00", "5.50");
        assertThat(order.getTotal()).isEqualByComparingTo("15.50");
        assertThatThrownBy(() -> order.applyRefund(new BigDecimal("20"))).isInstanceOf(IllegalArgumentException.class);
        order.applyRefund(new BigDecimal("15.50"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }
}
