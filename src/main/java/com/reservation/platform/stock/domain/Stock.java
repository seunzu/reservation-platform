package com.reservation.platform.stock.domain;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.stock.exception.StockErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int remaining;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void decrease() {
        if (this.remaining <= 0) {
            throw new ApplicationException(StockErrorCode.STOCK_EXHAUSTED);
        }
        this.remaining--;
        this.updatedAt = LocalDateTime.now();
    }

    public void increase() {
        this.remaining++;
        this.updatedAt = LocalDateTime.now();
    }
}