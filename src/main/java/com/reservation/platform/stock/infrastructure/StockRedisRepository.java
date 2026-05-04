package com.reservation.platform.stock.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockRedisRepository {

    private static final String STOCK_KEY = "stock:product:";

    private final StringRedisTemplate redisTemplate;

    public void initialize(Long productId, int stock) {
        redisTemplate.opsForValue().set(STOCK_KEY + productId, String.valueOf(stock));
    }

    public int getRemaining(Long productId) {
        String value = redisTemplate.opsForValue().get(STOCK_KEY + productId);
        if (value == null) {
            return -1;
        }
        return Integer.parseInt(value);
    }
}
