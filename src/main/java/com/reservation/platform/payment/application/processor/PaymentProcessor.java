package com.reservation.platform.payment.application.processor;

import com.reservation.platform.booking.application.dto.BookingRequest;
import com.reservation.platform.payment.domain.Payment;

public interface PaymentProcessor {

    boolean supports(BookingRequest request);
    PaymentResult process(Payment payment, BookingRequest request);
}