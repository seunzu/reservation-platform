package com.reservation.platform.payment.infrastructure;

import com.reservation.platform.payment.domain.PaymentTransaction;
import com.reservation.platform.payment.domain.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentTransactionRepositoryImpl implements PaymentTransactionRepository {

    private final PaymentTransactionJpaRepository paymentTransactionJpaRepository;

    @Override
    public PaymentTransaction save(PaymentTransaction transaction) {
        return paymentTransactionJpaRepository.save(transaction);
    }
}