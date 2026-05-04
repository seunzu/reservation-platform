package com.reservation.platform.stock.application;

import com.reservation.platform.stock.domain.repository.StockRepository;
import com.reservation.platform.stock.infrastructure.StockRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockRepository stockRepository;
    private final StockRedisRepository stockRedisRepository;

    public int getRemaining(Long productId) {
        try {
            int remaining = stockRedisRepository.getRemaining(productId);
            if (remaining == -1) {
                return stockRepository.findByProductId(productId).getRemaining();
            }
            return remaining;
        } catch (RedisConnectionFailureException e) {
            log.warn("[Stock] Redis 장애 — DB fallback for getRemaining productId={}", productId);
            return stockRepository.findByProductId(productId).getRemaining();
        }
    }
}
