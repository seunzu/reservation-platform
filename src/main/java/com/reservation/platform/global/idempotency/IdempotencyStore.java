package com.reservation.platform.global.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyStore {

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final long COMPLETED_TTL = 86400L;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(String idempotencyKey, Object responseBody) {
        try {
            String json = objectMapper.writeValueAsString(responseBody);
            redisTemplate.opsForValue()
                    .set(IDEMPOTENCY_PREFIX + idempotencyKey, json,
                            COMPLETED_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[IdempotencyStore] 저장 실패 key={}", idempotencyKey, e);
        }
    }

    public void delete(String idempotencyKey) {
        redisTemplate.delete(IDEMPOTENCY_PREFIX + idempotencyKey);
    }
}
