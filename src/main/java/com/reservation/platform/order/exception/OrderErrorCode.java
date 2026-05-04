package com.reservation.platform.order.exception;

import com.reservation.platform.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND("O001", "주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    DUPLICATE_ORDER("O002", "이미 처리된 주문입니다.", HttpStatus.CONFLICT),
    INVALID_ORDER_STATUS("O003", "유효하지 않은 주문 상태입니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}