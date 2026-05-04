package com.reservation.platform.product.exception;

import com.reservation.platform.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_FOUND("P001", "상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    SALE_NOT_OPENED("P002", "아직 판매가 시작되지 않았습니다.", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}