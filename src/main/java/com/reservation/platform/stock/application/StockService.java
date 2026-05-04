package com.reservation.platform.stock.application;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.global.exception.CommonErrorCode;
import com.reservation.platform.stock.domain.Stock;
import com.reservation.platform.stock.domain.repository.StockRepository;
import com.reservation.platform.stock.exception.StockErrorCode;
import com.reservation.platform.stock.infrastructure.StockRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockRepository stockRepository;
    private final StockRedisRepository stockRedisRepository;
    private final RedissonClient redissonClient;
    private final StockDbLockService stockDbLockService;

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

    public StockReservation reserve(Long productId) {
        try {
            initializeRedisStockIfAbsent(productId);
            long remaining = stockRedisRepository.decrease(productId);
            if (remaining < 0) {
                stockRedisRepository.increase(productId);
                throw new ApplicationException(StockErrorCode.STOCK_EXHAUSTED);
            }
            return StockReservation.redisReserved(productId);
        } catch (RedisConnectionFailureException e) {
            log.warn("[Stock] Redis 장애 — DB fallback for reserve productId={}", productId);
            stockDbLockService.decreaseWithLock(productId);
            return StockReservation.dbConfirmed(productId);
        }
    }

    public StockReservation confirm(StockReservation reservation) {
        if (reservation.dbConfirmed()) {
            return reservation;
        }
        stockDbLockService.decreaseWithLock(reservation.productId());
        return reservation.confirmDb();
    }

    public void release(StockReservation reservation) {
        if (reservation.redisReserved()) {
            try {
                stockRedisRepository.increase(reservation.productId());
            } catch (RedisConnectionFailureException e) {
                log.error("[Stock] Redis 재고 복구 실패 — reconciliation 필요 productId={}",
                        reservation.productId(), e);
            }
        }

        if (reservation.dbConfirmed()) {
            stockDbLockService.increaseWithLock(reservation.productId());
        }
    }

    private void initializeRedisStockIfAbsent(Long productId) {
        if (stockRedisRepository.exists(productId)) {
            return;
        }

        String lockKey = "lock:stock:init:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(1, 3, TimeUnit.SECONDS);
            if (!acquired) {
                throw new ApplicationException(CommonErrorCode.LOCK_ACQUISITION_FAILED);
            }
            if (!stockRedisRepository.exists(productId)) {
                Stock stock = stockRepository.findByProductId(productId);
                stockRedisRepository.initialize(productId, stock.getRemaining());
                log.info("[Stock] Redis 재고 초기화 productId={}, remaining={}",
                        productId, stock.getRemaining());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApplicationException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}
