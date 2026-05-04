package com.reservation.platform.payment.domain;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.payment.exception.PaymentErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private int cardAmount;

    @Column(nullable = false)
    private int pointAmount;

    @Column(nullable = false)
    private int totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String pgTransactionId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static Payment create(Long orderId, PaymentMethod paymentMethod,
                                 int cardAmount, int pointAmount) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.paymentMethod = paymentMethod;
        payment.cardAmount = cardAmount;
        payment.pointAmount = pointAmount;
        payment.totalAmount = cardAmount + pointAmount;
        payment.status = PaymentStatus.PENDING;
        payment.createdAt = LocalDateTime.now();
        return payment;
    }

    public void complete(String pgTransactionId) {
        validateStatus(PaymentStatus.PENDING);
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        validateStatus(PaymentStatus.PENDING);
        this.status = PaymentStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail() {
        validateStatus(PaymentStatus.PENDING);
        this.status = PaymentStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
        this.status = PaymentStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }


    private void validateStatus(PaymentStatus expected) {
        if (this.status != expected) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PAYMENT_STATUS);
        }
    }
}