package com.reservation.platform.payment.infrastructure;

import com.reservation.platform.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
}
