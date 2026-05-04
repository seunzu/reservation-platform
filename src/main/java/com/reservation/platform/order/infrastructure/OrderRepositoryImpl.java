package com.reservation.platform.order.infrastructure;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.order.domain.Order;
import com.reservation.platform.order.domain.repository.OrderRepository;
import com.reservation.platform.order.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Order findById(Long id) {
        return orderJpaRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    @Override
    public boolean existsByOrderToken(String orderToken) {
        return orderJpaRepository.existsByOrderToken(orderToken);
    }
}