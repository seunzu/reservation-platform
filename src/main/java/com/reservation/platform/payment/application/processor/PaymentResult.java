package com.reservation.platform.payment.application.processor;

public record PaymentResult(
        String pgTransactionId,
        boolean success,
        String failureReason,
        String rawResponse
) {}