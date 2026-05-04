package com.reservation.platform.payment.exception;

import com.reservation.platform.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_FAILED("PM001", "결제에 실패했습니다.", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_METHOD("PM002", "유효하지 않은 결제 수단입니다.", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_COMBINATION("PM003", "신용카드와 Y페이는 함께 사용할 수 없습니다.", HttpStatus.BAD_REQUEST),
    PAYMENT_AMOUNT_MISMATCH("PM004", "결제 금액이 상품 가격과 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_STATUS("PM005", "유효하지 않은 결제 상태입니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
