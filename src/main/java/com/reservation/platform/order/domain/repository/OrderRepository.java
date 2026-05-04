package com.reservation.platform.order.domain.repository;

import com.reservation.platform.order.domain.Order;

public interface OrderRepository {

    Order save(Order order);
    Order findById(Long id);
    boolean existsByOrderToken(String orderToken);
}