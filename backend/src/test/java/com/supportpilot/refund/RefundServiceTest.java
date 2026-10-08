package com.supportpilot.refund;

import static com.supportpilot.TestData.ALICE;
import static com.supportpilot.TestData.CLOCK;
import static com.supportpilot.TestData.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supportpilot.TestData;
import com.supportpilot.common.BusinessRuleException;
import com.supportpilot.common.NotFoundException;
import com.supportpilot.order.Order;
import com.supportpilot.order.OrderRepository;
import com.supportpilot.order.OrderService;
import com.supportpilot.order.OrderStatus;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    RefundRepository refunds;

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderService orders;

    RefundService service;

    @BeforeEach
    void setUp() {
        service = new RefundService(refunds, orderRepository, orders, CLOCK, TestData.properties());
    }

    private void given(Order order) {
        when(orders.getForCustomer(order.getId(), ALICE)).thenReturn(order);
    }

    private void saveReturnsArgument() {
        when(refunds.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsPendingRequestForFullBalanceByDefault() {
        given(order("ORD-1", OrderStatus.DELIVERED, 5));
        saveReturnsArgument();

        RefundRequest r = service.requestRefund("ORD-1", ALICE, null, " Stopped working ", "conv-1");

        assertThat(r.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(r.getAmount()).isEqualByComparingTo("129.99");
        assertThat(r.getReason()).isEqualTo("Stopped working");
        assertThat(r.getConversationId()).isEqualTo("conv-1");
    }

    @Test
    void acceptsPartialAmountRoundedToCents() {
        given(order("ORD-1", OrderStatus.DELIVERED, 5, "100.00", "50.00"));
        saveReturnsArgument();

        RefundRequest r = service.requestRefund("ORD-1", ALICE, new BigDecimal("49.999"), "One item broken", null);

        assertThat(r.getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void rejectsRefundOutsideWindow() {
        given(order("ORD-1", OrderStatus.DELIVERED, 31));
        assertThatThrownBy(() -> service.requestRefund("ORD-1", ALICE, null, "late", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("outside the refund window");
        verify(refunds, never()).saveAndFlush(any());
    }

    @Test
    void allowsRefundOnLastDayOfWindow() {
        given(order("ORD-1", OrderStatus.DELIVERED, 30));
        saveReturnsArgument();
        assertThat(service.requestRefund("ORD-1", ALICE, null, "changed my mind", null)).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PROCESSING", "SHIPPED", "CANCELLED", "REFUNDED"})
    void rejectsOrdersThatAreNotDelivered(OrderStatus status) {
        given(order("ORD-1", status, null));
        assertThatThrownBy(() -> service.requestRefund("ORD-1", ALICE, null, "reason", null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsAmountAboveBalanceAndNonPositiveAmounts() {
        given(order("ORD-1", OrderStatus.DELIVERED, 5));
        assertThatThrownBy(() -> service.requestRefund("ORD-1", ALICE, new BigDecimal("500"), "r", null))
                .hasMessageContaining("exceeds the refundable balance");
        assertThatThrownBy(() -> service.requestRefund("ORD-1", ALICE, BigDecimal.ZERO, "r", null))
                .hasMessageContaining("greater than zero");
    }

    @Test
    void requiresReason() {
        given(order("ORD-1", OrderStatus.DELIVERED, 5));
        assertThatThrownBy(() -> service.requestRefund("ORD-1", ALICE, null, "  ", null))
                .hasMessageContaining("reason");
    }

    @Test
    void rejectsSecondPendingRequest() {
        given(order("ORD-1", OrderStatus.DELIVERED, 5));
        when(refunds.existsByOrderIdAndStatus("ORD-1", RefundStatus.PENDING)).thenReturn(true);
        assertThatThrownBy(() -> service.requestRefund("ORD-1", ALICE, null, "r", null))
                .hasMessageContaining("already a pending refund");
    }

    @Test
    void concurrentDuplicateIsReportedAsBusinessRule() {
        given(order("ORD-1", OrderStatus.DELIVERED, 5));
        when(refunds.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));
        assertThatThrownBy(() -> service.requestRefund("ORD-1", ALICE, null, "r", null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void propagatesNotFoundForOtherCustomersOrders() {
        when(orders.getForCustomer("ORD-9", ALICE)).thenThrow(new NotFoundException("No order"));
        assertThatThrownBy(() -> service.requestRefund("ORD-9", ALICE, null, "r", null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void approveAppliesRefundToOrder() {
        Order o = order("ORD-1", OrderStatus.DELIVERED, 5);
        RefundRequest r = RefundRequest.pending("ORD-1", ALICE, new BigDecimal("129.99"), "r", null, TestData.NOW);
        when(refunds.findById(r.getId())).thenReturn(Optional.of(r));
        when(orderRepository.findById("ORD-1")).thenReturn(Optional.of(o));

        RefundRequest approved = service.approve(r.getId(), "Dana", "Approved, sorry!");

        assertThat(approved.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(approved.getDecidedBy()).isEqualTo("Dana");
        assertThat(approved.getDecidedAt()).isEqualTo(TestData.NOW);
        assertThat(o.getRefundedAmount()).isEqualByComparingTo("129.99");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void partialApprovalKeepsOrderDelivered() {
        Order o = order("ORD-1", OrderStatus.DELIVERED, 5, "100.00", "50.00");
        RefundRequest r = RefundRequest.pending("ORD-1", ALICE, new BigDecimal("50.00"), "r", null, TestData.NOW);
        when(refunds.findById(r.getId())).thenReturn(Optional.of(r));
        when(orderRepository.findById("ORD-1")).thenReturn(Optional.of(o));

        service.approve(r.getId(), "Dana", null);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(o.refundableBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectLeavesOrderUntouchedAndDecisionIsFinal() {
        RefundRequest r = RefundRequest.pending("ORD-1", ALICE, BigDecimal.TEN, "r", null, TestData.NOW);
        when(refunds.findById(r.getId())).thenReturn(Optional.of(r));

        service.reject(r.getId(), "Dana", "Outside policy");

        assertThat(r.getStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(r.getDecisionNote()).isEqualTo("Outside policy");
        assertThatThrownBy(() -> service.approve(r.getId(), "Dana", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already REJECTED");
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void unknownRequestIsNotFound() {
        UUID id = UUID.randomUUID();
        when(refunds.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reject(id, "Dana", null)).isInstanceOf(NotFoundException.class);
    }
}
