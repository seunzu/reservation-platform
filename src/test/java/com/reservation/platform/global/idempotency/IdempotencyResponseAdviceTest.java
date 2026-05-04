package com.reservation.platform.global.idempotency;

import com.reservation.platform.global.exception.CommonErrorCode;
import com.reservation.platform.global.response.ApiResponse;
import com.reservation.platform.payment.exception.PaymentErrorCode;
import com.reservation.platform.stock.exception.StockErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyResponseAdviceTest {

    // ── 성공 응답 → save ──────────────────────────────────────────────────────

    @Test
    void beforeBodyWrite_savesSuccessResponse() {
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        IdempotencyResponseAdvice advice = new IdempotencyResponseAdvice(store);

        advice.beforeBodyWrite(
                ApiResponse.ok("booking-result"),
                null, null, null,
                new ServletServerHttpRequest(requestWithKey("key-1")),
                null
        );

        assertEquals("key-1", store.savedKey);
        assertNull(store.deletedKey);
    }

    @Test
    void beforeBodyWrite_savesSuccessResponseWithNullData() {
        // data가 null이어도 success=true면 저장
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        IdempotencyResponseAdvice advice = new IdempotencyResponseAdvice(store);

        advice.beforeBodyWrite(
                ApiResponse.ok(),
                null, null, null,
                new ServletServerHttpRequest(requestWithKey("key-2")),
                null
        );

        assertEquals("key-2", store.savedKey);
        assertNull(store.deletedKey);
    }

    // ── 실패 응답 → delete (재시도 허용) ─────────────────────────────────────────

    @Test
    void beforeBodyWrite_deletesKeyOnInternalServerError() {
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        IdempotencyResponseAdvice advice = new IdempotencyResponseAdvice(store);

        advice.beforeBodyWrite(
                ApiResponse.fail(CommonErrorCode.INTERNAL_SERVER_ERROR),
                null, null, null,
                new ServletServerHttpRequest(requestWithKey("key-3")),
                null
        );

        assertNull(store.savedKey);
        assertEquals("key-3", store.deletedKey);
    }

    @Test
    void beforeBodyWrite_deletesKeyOnBusinessError() {
        // 재고 소진 같은 비즈니스 실패도 재시도 허용
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        IdempotencyResponseAdvice advice = new IdempotencyResponseAdvice(store);

        advice.beforeBodyWrite(
                ApiResponse.fail(StockErrorCode.STOCK_EXHAUSTED),
                null, null, null,
                new ServletServerHttpRequest(requestWithKey("key-4")),
                null
        );

        assertNull(store.savedKey);
        assertEquals("key-4", store.deletedKey);
    }

    @Test
    void beforeBodyWrite_deletesKeyOnPaymentError() {
        // 결제 실패도 캐싱하지 않고 삭제 → 동일 key로 재시도 가능
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        IdempotencyResponseAdvice advice = new IdempotencyResponseAdvice(store);

        advice.beforeBodyWrite(
                ApiResponse.fail(PaymentErrorCode.PAYMENT_FAILED),
                null, null, null,
                new ServletServerHttpRequest(requestWithKey("key-5")),
                null
        );

        assertNull(store.savedKey);
        assertEquals("key-5", store.deletedKey);
    }

    // ── idempotency key attribute 없음 → 아무것도 하지 않음 ─────────────────────

    @Test
    void beforeBodyWrite_doesNothingWhenKeyAttributeIsAbsent() {
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        IdempotencyResponseAdvice advice = new IdempotencyResponseAdvice(store);
        MockHttpServletRequest requestWithoutKey = new MockHttpServletRequest();

        advice.beforeBodyWrite(
                ApiResponse.ok("data"),
                null, null, null,
                new ServletServerHttpRequest(requestWithoutKey),
                null
        );

        assertNull(store.savedKey);
        assertNull(store.deletedKey);
    }

    @Test
    void beforeBodyWrite_doesNothingWhenBodyIsNotApiResponse() {
        // ApiResponse가 아닌 body는 처리하지 않음
        CapturingIdempotencyStore store = new CapturingIdempotencyStore();
        IdempotencyResponseAdvice advice = new IdempotencyResponseAdvice(store);

        advice.beforeBodyWrite(
                "plain string body",
                null, null, null,
                new ServletServerHttpRequest(requestWithKey("key-6")),
                null
        );

        assertNull(store.savedKey);
        assertNull(store.deletedKey);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private MockHttpServletRequest requestWithKey(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("X-Idempotency-Key", key);
        return request;
    }

    // ── Test Double ───────────────────────────────────────────────────────────

    private static class CapturingIdempotencyStore extends IdempotencyStore {
        String savedKey;
        Object savedBody;
        String deletedKey;

        private CapturingIdempotencyStore() {
            super(null, null);
        }

        @Override
        public void save(String idempotencyKey, Object responseBody) {
            this.savedKey = idempotencyKey;
            this.savedBody = responseBody;
        }

        @Override
        public void delete(String idempotencyKey) {
            this.deletedKey = idempotencyKey;
        }
    }
}