package com.reservation.platform.payment.infrastructure;

import com.reservation.platform.payment.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionJpaRepository extends JpaRepository<PaymentTransaction, Long> {
}
