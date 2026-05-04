package com.reservation.platform.point.exception;

import com.reservation.platform.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PointErrorCode implements ErrorCode {

    POINT_NOT_FOUND("PT001", "포인트 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INSUFFICIENT_POINT("PT002", "포인트가 부족합니다.", HttpStatus.BAD_REQUEST),
    INVALID_POINT_AMOUNT("PT003", "포인트 사용 금액이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
