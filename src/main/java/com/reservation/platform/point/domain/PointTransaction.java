package com.reservation.platform.point.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "point_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long orderId;

    @Column(nullable = false)
    private Long pointId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointTransactionType type;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private long balanceAfter;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PointTransaction use(Point point, Long orderId, int amount) {
        PointTransaction tx = new PointTransaction();
        tx.userId = point.getUserId();
        tx.orderId = orderId;
        tx.pointId = point.getId();
        tx.type = PointTransactionType.USE;
        tx.amount = amount;
        tx.balanceAfter = point.getBalance();
        tx.createdAt = LocalDateTime.now();
        return tx;
    }

    public static PointTransaction refund(Point point, Long orderId, int amount) {
        PointTransaction tx = new PointTransaction();
        tx.userId = point.getUserId();
        tx.orderId = orderId;
        tx.pointId = point.getId();
        tx.type = PointTransactionType.REFUND;
        tx.amount = amount;
        tx.balanceAfter = point.getBalance();
        tx.createdAt = LocalDateTime.now();
        return tx;
    }
}