package com.supportpilot.refund;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<RefundRequest, UUID> {

    List<RefundRequest> findByStatusOrderByCreatedAtAsc(RefundStatus status);

    List<RefundRequest> findAllByOrderByCreatedAtDesc();

    List<RefundRequest> findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    boolean existsByOrderIdAndStatus(String orderId, RefundStatus status);
}
