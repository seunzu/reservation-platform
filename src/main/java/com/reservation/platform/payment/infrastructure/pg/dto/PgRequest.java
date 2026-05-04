package com.reservation.platform.payment.infrastructure.pg.dto;

public record PgRequest(
        String orderToken,
        int amount,
        String paymentMethod
) {}
