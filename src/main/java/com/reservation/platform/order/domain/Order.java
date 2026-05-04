package com.reservation.platform.order.domain;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.order.exception.OrderErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, unique = true)
    private String orderToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private int totalAmount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static Order create(Long userId, Long productId,
                               String orderToken, int totalAmount) {
        Order order = new Order();
        order.userId = userId;
        order.productId = productId;
        order.orderToken = orderToken;
        order.status = OrderStatus.PENDING;
        order.totalAmount = totalAmount;
        order.createdAt = LocalDateTime.now();
        return order;
    }

    public void confirm() {
        validateStatus(OrderStatus.PENDING);
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail() {
        validateStatus(OrderStatus.PENDING);
        this.status = OrderStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    private void validateStatus(OrderStatus expected) {
        if (this.status != expected) {
            throw new ApplicationException(OrderErrorCode.INVALID_ORDER_STATUS);
        }
    }
}