package com.reservation.platform.order.application;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.order.domain.Order;
import com.reservation.platform.order.domain.repository.OrderRepository;
import com.reservation.platform.order.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order create(Long userId, Long productId,
                        String orderToken, int totalAmount) {
        if (orderRepository.existsByOrderToken(orderToken)) {
            throw new ApplicationException(OrderErrorCode.DUPLICATE_ORDER);
        }

        Order order = Order.create(userId, productId, orderToken, totalAmount);
        orderRepository.save(order);
        log.info("[Order] 주문 생성 orderId={}, orderToken={}", order.getId(), orderToken);
        return order;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order confirm(Long orderId) {
        Order order = orderRepository.findById(orderId);
        order.confirm();
        return orderRepository.save(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order fail(Long orderId) {
        Order order = orderRepository.findById(orderId);
        order.fail();
        return orderRepository.save(order);
    }
}
