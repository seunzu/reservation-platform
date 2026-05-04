package com.reservation.platform.global.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.reservation.platform.global.response.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class IdempotencyResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final String IDEMPOTENCY_KEY_ATTR = "X-Idempotency-Key";

    private final IdempotencyStore idempotencyStore;

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            String key = (String) servletRequest.getServletRequest()
                    .getAttribute(IDEMPOTENCY_KEY_ATTR);
            if (key != null) {
                if (!(body instanceof ApiResponse<?> apiResponse)) {
                    return body;
                }
                if (apiResponse.success()) {
                    idempotencyStore.save(key, body);
                } else {
                    idempotencyStore.delete(key);
                }
            }
        }
        return body;
    }
}
