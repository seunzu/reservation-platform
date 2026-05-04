package com.reservation.platform.payment.domain.repository;

import com.reservation.platform.payment.domain.PaymentTransaction;

public interface PaymentTransactionRepository {

    PaymentTransaction save(PaymentTransaction transaction);
}
