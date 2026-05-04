package com.reservation.platform.payment.application.processor;

import com.reservation.platform.booking.application.dto.BookingRequest;
import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.payment.domain.Payment;
import com.reservation.platform.payment.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompositePaymentProcessor {

    private final List<PaymentProcessor> processors;

    public PaymentResult process(Payment payment, BookingRequest request) {
        PaymentProcessor processor = processors.stream()
                .filter(p -> p.supports(request))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(PaymentErrorCode.INVALID_PAYMENT_METHOD));

        log.info("[CompositePayment] processor={} 선택", processor.getClass().getSimpleName());
        return processor.process(payment, request);
    }
}