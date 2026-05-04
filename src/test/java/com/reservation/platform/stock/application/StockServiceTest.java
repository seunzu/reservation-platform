package com.reservation.platform.stock.application;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.stock.domain.Stock;
import com.reservation.platform.stock.domain.repository.StockRepository;
import com.reservation.platform.stock.exception.StockErrorCode;
import com.reservation.platform.stock.infrastructure.StockRedisRepository;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StockServiceTest {

    // ── Redis key 없음 → DB 원장으로 초기화 후 선점 ────────────────────────────

    @Test
    void reserve_initializesRedisFromDbWhenKeyIsMissing() throws Exception {
        FakeStockRedisRepository redis = new FakeStockRedisRepository();
        FakeStockRepository db = new FakeStockRepository(1L, 10);
        RedissonClient redissonClient = mockLock();

        StockService svc = stockService(db, redis, redissonClient);
        StockReservation reservation = svc.reserve(1L);

        // Redis 초기화(10) 후 DECR → 9
        assertTrue(reservation.redisReserved());
        assertFalse(reservation.dbConfirmed());
        assertEquals(9, redis.getRemaining(1L));
    }

    @Test
    void reserve_skipInitializationWhenRedisKeyExists() throws Exception {
        FakeStockRedisRepository redis = new FakeStockRedisRepository();
        redis.initialize(1L, 5); // key 이미 존재
        FakeStockRepository db = new FakeStockRepository(1L, 10);
        RedissonClient redissonClient = mock(RedissonClient.class); // lock 호출 없어야 함

        StockService svc = stockService(db, redis, redissonClient);
        svc.reserve(1L);

        // getLock 자체가 호출되지 않아야 함 — 초기화 분기 진입 안 함
        verify(redissonClient, never()).getLock(anyString());
        assertEquals(4, redis.getRemaining(1L));
    }

    @Test
    void reserve_throwsStockExhaustedWhenRedisBecomesMinus() throws Exception {
        FakeStockRedisRepository redis = new FakeStockRedisRepository();
        redis.initialize(1L, 0); // 재고 0
        FakeStockRepository db = new FakeStockRepository(1L, 0);
        RedissonClient redissonClient = mock(RedissonClient.class);

        StockService svc = stockService(db, redis, redissonClient);

        ApplicationException ex = assertThrows(ApplicationException.class, () -> svc.reserve(1L));
        assertEquals(StockErrorCode.STOCK_EXHAUSTED, ex.getErrorCode());
        // INCR 복구로 다시 0으로 돌아와야 함
        assertEquals(0, redis.getRemaining(1L));
    }

    // ── confirm: Redis 선점 → DB 확정 차감 ────────────────────────────────────

    @Test
    void confirm_decreasesDbStockWhenRedisReservation() {
        FakeStockRedisRepository redis = new FakeStockRedisRepository();
        FakeStockRepository db = new FakeStockRepository(1L, 10);
        StockService svc = stockService(db, redis, mock(RedissonClient.class));

        StockReservation confirmed = svc.confirm(StockReservation.redisReserved(1L));

        assertTrue(confirmed.dbConfirmed());
        assertEquals(9, db.stock.getRemaining());
    }

    @Test
    void confirm_skipsDbDecreaseWhenAlreadyDbConfirmed() {
        // DB fallback 경로에서 이미 dbConfirmed된 예약은 중복 차감하지 않음
        FakeStockRedisRepository redis = new FakeStockRedisRepository();
        FakeStockRepository db = new FakeStockRepository(1L, 10);
        StockService svc = stockService(db, redis, mock(RedissonClient.class));

        StockReservation dbConfirmed = StockReservation.dbConfirmed(1L);
        StockReservation result = svc.confirm(dbConfirmed);

        assertTrue(result.dbConfirmed());
        assertEquals(10, db.stock.getRemaining()); // DB 차감 없음
    }

    // ── release: 실패 보상 → Redis + DB 복구 ────────────────────────────────────

    @Test
    void release_restoresOnlyRedisWhenOnlyRedisReserved() {
        FakeStockRedisRepository redis = new FakeStockRedisRepository();
        redis.initialize(1L, 9);
        FakeStockRepository db = new FakeStockRepository(1L, 9);
        StockService svc = stockService(db, redis, mock(RedissonClient.class));

        // redisReserved=true, dbConfirmed=false
        svc.release(StockReservation.redisReserved(1L));

        assertEquals(10, redis.getRemaining(1L)); // Redis 복구
        assertEquals(9, db.stock.getRemaining());  // DB 복구 없음
    }

    @Test
    void release_restoresBothRedisAndDbWhenFullyConfirmed() {
        FakeStockRedisRepository redis = new FakeStockRedisRepository();
        redis.initialize(1L, 9);
        FakeStockRepository db = new FakeStockRepository(1L, 9);
        StockService svc = stockService(db, redis, mock(RedissonClient.class));

        // redisReserved=true, dbConfirmed=true
        svc.release(new StockReservation(1L, true, true));

        assertEquals(10, redis.getRemaining(1L)); // Redis 복구
        assertEquals(10, db.stock.getRemaining()); // DB 복구
    }

    @Test
    void release_restoresOnlyDbWhenDbFallback() {
        FakeStockRedisRepository redis = new FakeStockRedisRepository();
        FakeStockRepository db = new FakeStockRepository(1L, 9);
        StockService svc = stockService(db, redis, mock(RedissonClient.class));

        // DB fallback 경로: redisReserved=false, dbConfirmed=true
        svc.release(StockReservation.dbConfirmed(1L));

        assertEquals(-1, redis.getRemaining(1L)); // Redis 키 없음 (복구 안 함)
        assertEquals(10, db.stock.getRemaining()); // DB 복구
    }

    // ── Redis 장애 fallback ────────────────────────────────────────────────────

    @Test
    void reserve_fallsBackToDbLockWhenRedisThrows() {
        ThrowingStockRedisRepository redis = new ThrowingStockRedisRepository();
        FakeStockRepository db = new FakeStockRepository(1L, 10);
        StockService svc = stockService(db, redis, mock(RedissonClient.class));

        StockReservation reservation = svc.reserve(1L);

        assertFalse(reservation.redisReserved());
        assertTrue(reservation.dbConfirmed());
        assertEquals(9, db.stock.getRemaining()); // DB 비관적 락으로 차감
    }

    @Test
    void getRemaining_fallsBackToDbWhenRedisThrows() {
        ThrowingStockRedisRepository redis = new ThrowingStockRedisRepository();
        FakeStockRepository db = new FakeStockRepository(1L, 7);
        StockService svc = stockService(db, redis, mock(RedissonClient.class));

        int remaining = svc.getRemaining(1L);

        assertEquals(7, remaining); // DB 원장 기준
    }

    // ── 헬퍼: lock mock ────────────────────────────────────────────────────────

    private RedissonClient mockLock() throws InterruptedException {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(1, 3, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        return client;
    }

    private StockService stockService(FakeStockRepository db,
                                      StockRedisRepository redis,
                                      RedissonClient redissonClient) {
        return new StockService(db, redis, redissonClient, new StockDbLockService(db));
    }

    // ── Fake 구현체 ────────────────────────────────────────────────────────────

    private static class FakeStockRedisRepository extends StockRedisRepository {
        private final Map<Long, Integer> stocks = new HashMap<>();

        private FakeStockRedisRepository() {
            super((StringRedisTemplate) null);
        }

        @Override
        public void initialize(Long productId, int stock) {
            stocks.put(productId, stock);
        }

        @Override
        public boolean exists(Long productId) {
            return stocks.containsKey(productId);
        }

        @Override
        public long decrease(Long productId) {
            int next = stocks.getOrDefault(productId, 0) - 1;
            stocks.put(productId, next);
            return next;
        }

        @Override
        public void increase(Long productId) {
            stocks.put(productId, stocks.getOrDefault(productId, 0) + 1);
        }

        @Override
        public int getRemaining(Long productId) {
            return stocks.getOrDefault(productId, -1);
        }
    }

    // Redis 연결 실패를 시뮬레이션하는 구현체
    private static class ThrowingStockRedisRepository extends StockRedisRepository {

        private ThrowingStockRedisRepository() {
            super(null);
        }

        @Override
        public boolean exists(Long productId) {
            throw new RedisConnectionFailureException("Redis is down");
        }

        @Override
        public int getRemaining(Long productId) {
            throw new RedisConnectionFailureException("Redis is down");
        }
    }

    private static class FakeStockRepository implements StockRepository {
        private final Stock stock;

        private FakeStockRepository(Long productId, int remaining) {
            this.stock = BeanUtils.instantiateClass(Stock.class);
            ReflectionTestUtils.setField(stock, "productId", productId);
            ReflectionTestUtils.setField(stock, "remaining", remaining);
            ReflectionTestUtils.setField(stock, "updatedAt", LocalDateTime.now());
        }

        @Override
        public Stock findByProductId(Long productId) {
            return stock;
        }

        @Override
        public Stock findByProductIdWithLock(Long productId) {
            return stock;
        }
    }
}
