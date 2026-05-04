package com.reservation.platform.payment.application;

import com.reservation.platform.booking.application.dto.BookingRequest;
import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.order.domain.Order;
import com.reservation.platform.payment.application.processor.CompositePaymentProcessor;
import com.reservation.platform.payment.application.processor.PaymentResult;
import com.reservation.platform.payment.domain.Payment;
import com.reservation.platform.payment.domain.PaymentTransaction;
import com.reservation.platform.payment.domain.repository.PaymentRepository;
import com.reservation.platform.payment.domain.repository.PaymentTransactionRepository;
import com.reservation.platform.payment.exception.PaymentErrorCode;
import com.reservation.platform.payment.infrastructure.pg.PgClient;
import com.reservation.platform.payment.infrastructure.pg.dto.PgResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CompositePaymentProcessor compositePaymentProcessor;
    private final PgClient pgClient;

    public Payment create(Order order, BookingRequest request) {
        Payment payment = Payment.create(
                order.getId(),
                request.primaryMethod(),
                request.cardAmount(),
                request.pointAmount()
        );
        return paymentRepository.save(payment);
    }

    public Payment approve(Payment payment, BookingRequest request) {
        if (payment.getCardAmount() > 0) {
            PaymentResult result = compositePaymentProcessor.process(payment, request);

            if (!result.success()) {
                paymentTransactionRepository.save(
                        PaymentTransaction.fail(payment.getId(), payment.getCardAmount(), result.failureReason())
                );
                payment.fail();
                paymentRepository.save(payment);
                throw new ApplicationException(PaymentErrorCode.PAYMENT_FAILED, result.failureReason());
            }

            payment.complete(result.pgTransactionId());
            paymentTransactionRepository.save(
                    PaymentTransaction.approve(
                            payment.getId(),
                            result.pgTransactionId(),
                            payment.getCardAmount(),
                            result.rawResponse()
                    )
            );
        } else {
            payment.complete();
        }

        paymentRepository.save(payment);
        log.info("[Payment] 결제 완료 paymentId={}", payment.getId());
        return payment;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(Payment payment) {
        if (payment.getPgTransactionId() == null) return;
        try {
            PgResponse response = pgClient.cancel(payment.getPgTransactionId());
            if (response.success()) {
                paymentTransactionRepository.save(
                        PaymentTransaction.cancel(payment.getId(), payment.getPgTransactionId(),
                                payment.getCardAmount(), response.rawResponse()));
                payment.cancel();
                paymentRepository.save(payment);
            } else {
                log.error("[Payment] PG 취소 실패 — 수동 처리 필요 paymentId={}", payment.getId());
            }
        } catch (Exception e) {
            log.error("[Payment] PG 취소 예외 — 수동 처리 필요 paymentId={}", payment.getId(), e);
        }
    }
}
