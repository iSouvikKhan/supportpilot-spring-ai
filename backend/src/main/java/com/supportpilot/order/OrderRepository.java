package com.supportpilot.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByCustomerEmailIgnoreCaseOrderByOrderedAtDesc(String customerEmail);

    @Query("select distinct o.customerEmail as email, o.customerName as name "
            + "from Order o order by o.customerName")
    List<CustomerView> findCustomers();

    interface CustomerView {
        String getEmail();

        String getName();
    }
}
