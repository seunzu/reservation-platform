package com.reservation.platform.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_ERROR("C001", "입력값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    BAD_REQUEST("C002", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED("C003", "지원하지 않는 HTTP 메서드입니다.", HttpStatus.METHOD_NOT_ALLOWED),
    INTERNAL_SERVER_ERROR("C004", "서버 내부 오류입니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    DUPLICATE_REQUEST("C005", "이미 처리 중인 요청입니다.", HttpStatus.CONFLICT),
    LOCK_ACQUISITION_FAILED("C006", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    MISSING_IDEMPOTENCY_KEY("C007", "X-Idempotency-Key 헤더가 필요합니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
