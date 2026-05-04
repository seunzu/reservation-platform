package com.reservation.platform.payment.application;

import com.reservation.platform.booking.application.dto.BookingRequest;
import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.payment.domain.PaymentMethod;
import com.reservation.platform.payment.exception.PaymentErrorCode;
import com.reservation.platform.product.domain.Product;
import org.springframework.stereotype.Component;

@Component
public class PaymentValidator {

    public void validate(BookingRequest request, Product product) {
        validatePaymentCombination(request);
        validateAmount(request, product);
        validatePrimaryAmount(request);
        validatePointUsage(request);
    }

    private void validatePaymentCombination(BookingRequest request) {
        if (request.primaryMethod() == null) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PAYMENT_METHOD);
        }
    }

    private void validateAmount(BookingRequest request, Product product) {
        int total = request.cardAmount() + request.pointAmount();
        if (total != product.getPrice()) {
            throw new ApplicationException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    private void validatePrimaryAmount(BookingRequest request) {
        if (request.primaryMethod() == PaymentMethod.POINT) {
            if (request.cardAmount() != 0) {
                throw new ApplicationException(PaymentErrorCode.INVALID_PAYMENT_METHOD);
            }
            return;
        }

        if (request.cardAmount() <= 0) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PAYMENT_METHOD);
        }
    }

    private void validatePointUsage(BookingRequest request) {
        if (request.primaryMethod() == PaymentMethod.POINT && !request.usePoint()) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PAYMENT_METHOD);
        }

        if (request.usePoint() && request.pointAmount() <= 0) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PAYMENT_METHOD);
        }

        if (!request.usePoint() && request.pointAmount() > 0) {
            throw new ApplicationException(PaymentErrorCode.INVALID_PAYMENT_METHOD);
        }
    }
}
