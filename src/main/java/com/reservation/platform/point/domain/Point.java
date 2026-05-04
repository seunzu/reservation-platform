package com.reservation.platform.point.domain;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.point.exception.PointErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Point {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long balance;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void use(int amount) {
        if (this.balance < amount) {
            throw new ApplicationException(PointErrorCode.INSUFFICIENT_POINT);
        }
        this.balance -= amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void refund(int amount) {
        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
    }
}