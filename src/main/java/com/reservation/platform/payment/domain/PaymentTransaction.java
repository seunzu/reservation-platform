package com.reservation.platform.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(unique = true)
    private String pgTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    private String failureReason;

    @Column(columnDefinition = "TEXT")
    private String rawResponse;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static PaymentTransaction approve(Long paymentId, String pgTransactionId,
                                             int amount, String rawResponse) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.paymentId = paymentId;
        tx.pgTransactionId = pgTransactionId;
        tx.type = TransactionType.APPROVE;
        tx.amount = amount;
        tx.status = TransactionStatus.SUCCESS;
        tx.rawResponse = rawResponse;
        tx.createdAt = LocalDateTime.now();
        return tx;
    }

    public static PaymentTransaction fail(Long paymentId, int amount,
                                          String failureReason) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.paymentId = paymentId;
        tx.type = TransactionType.APPROVE;
        tx.amount = amount;
        tx.status = TransactionStatus.FAILED;
        tx.failureReason = failureReason;
        tx.createdAt = LocalDateTime.now();
        return tx;
    }

    public static PaymentTransaction cancel(Long paymentId, String pgTransactionId,
                                            int amount, String rawResponse) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.paymentId = paymentId;
        tx.pgTransactionId = pgTransactionId;
        tx.type = TransactionType.CANCEL;
        tx.amount = amount;
        tx.status = TransactionStatus.SUCCESS;
        tx.rawResponse = rawResponse;
        tx.createdAt = LocalDateTime.now();
        return tx;
    }
}