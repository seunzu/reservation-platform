package com.reservation.platform.product.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private LocalDateTime checkInAt;

    @Column(nullable = false)
    private LocalDateTime checkOutAt;

    @Column(nullable = false)
    private LocalDateTime saleOpenAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isSaleOpen() {
        return LocalDateTime.now().isAfter(saleOpenAt);
    }
}
