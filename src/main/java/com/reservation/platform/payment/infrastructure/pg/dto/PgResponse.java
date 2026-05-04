package com.reservation.platform.payment.infrastructure.pg.dto;

public record PgResponse(
        String pgTransactionId,
        boolean success,
        String failureReason,
        String rawResponse
) {}
