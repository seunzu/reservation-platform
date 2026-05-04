package com.reservation.platform.global.idempotency;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.global.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final String PROCESSING = "PROCESSING";
    private static final long PROCESSING_TTL = 300L;    // 5분

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApplicationException(CommonErrorCode.MISSING_IDEMPOTENCY_KEY);
        }

        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        String cached = redisTemplate.opsForValue().get(redisKey);

        if (PROCESSING.equals(cached)) {
            log.warn("[Idempotency] 처리 중인 요청 key={}", idempotencyKey);
            throw new ApplicationException(CommonErrorCode.DUPLICATE_REQUEST);
        }

        if (cached != null) {
            log.info("[Idempotency] 캐싱된 응답 반환 key={}", idempotencyKey);
            writeCachedResponse(response, cached);
            return false;
        }

        Boolean marked = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, PROCESSING, PROCESSING_TTL, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(marked)) {
            String latest = redisTemplate.opsForValue().get(redisKey);
            if (latest != null && !PROCESSING.equals(latest)) {
                log.info("[Idempotency] 경쟁 상태에서 캐싱된 응답 반환 key={}", idempotencyKey);
                writeCachedResponse(response, latest);
                return false;
            }
            log.warn("[Idempotency] 처리 중인 요청 key={}", idempotencyKey);
            throw new ApplicationException(CommonErrorCode.DUPLICATE_REQUEST);
        }

        request.setAttribute(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        return true;
    }

    private void writeCachedResponse(HttpServletResponse response, String cached) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(cached);
    }
}
