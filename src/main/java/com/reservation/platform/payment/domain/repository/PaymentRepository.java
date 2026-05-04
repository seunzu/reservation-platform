package com.reservation.platform.payment.domain.repository;

import com.reservation.platform.payment.domain.Payment;

public interface PaymentRepository {

    Payment save(Payment payment);
}