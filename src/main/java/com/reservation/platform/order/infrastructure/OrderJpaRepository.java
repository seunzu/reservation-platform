package com.reservation.platform.order.infrastructure;

import com.reservation.platform.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    boolean existsByOrderToken(String orderToken);
}