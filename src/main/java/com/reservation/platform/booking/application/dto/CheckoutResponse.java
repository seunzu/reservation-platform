package com.reservation.platform.booking.application.dto;

import com.reservation.platform.product.domain.Product;

import java.time.LocalDateTime;

public record CheckoutResponse(
        Long productId,
        String productName,
        int price,
        LocalDateTime checkInAt,
        LocalDateTime checkOutAt,
        LocalDateTime saleOpenAt,
        int remainingStock,
        long availablePoint
) {
    public static CheckoutResponse of(Product product, int remaining, long availablePoint) {
        return new CheckoutResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCheckInAt(),
                product.getCheckOutAt(),
                product.getSaleOpenAt(),
                remaining,
                availablePoint
        );
    }
}