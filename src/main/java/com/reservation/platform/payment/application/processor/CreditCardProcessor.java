package com.reservation.platform.payment.application.processor;

import com.reservation.platform.booking.application.dto.BookingRequest;
import com.reservation.platform.payment.domain.Payment;
import com.reservation.platform.payment.domain.PaymentMethod;
import com.reservation.platform.payment.infrastructure.pg.PgClient;
import com.reservation.platform.payment.infrastructure.pg.dto.PgRequest;
import com.reservation.platform.payment.infrastructure.pg.dto.PgResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditCardProcessor implements PaymentProcessor {

    private final PgClient pgClient;

    @Override
    public boolean supports(BookingRequest request) {
        return request.primaryMethod() == PaymentMethod.CREDIT_CARD;
    }

    @Override
    public PaymentResult process(Payment payment, BookingRequest request) {
        log.info("[CreditCard] 결제 시작 orderId={}, amount={}", payment.getOrderId(), payment.getCardAmount());

        PgResponse response = pgClient.approve(new PgRequest(
                request.orderToken(),
                payment.getCardAmount(),
                "CREDIT_CARD"
        ));

        return new PaymentResult(
                response.pgTransactionId(),
                response.success(),
                response.failureReason(),
                response.rawResponse()
        );
    }
}