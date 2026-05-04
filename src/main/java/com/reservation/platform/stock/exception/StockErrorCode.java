package com.reservation.platform.stock.exception;

import com.reservation.platform.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StockErrorCode implements ErrorCode {

    STOCK_EXHAUSTED("S001", "재고가 소진되었습니다.", HttpStatus.CONFLICT),
    STOCK_NOT_FOUND("S002", "재고 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}