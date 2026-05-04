package com.reservation.platform.interfaces.api;

import com.reservation.platform.booking.application.BookingFacade;
import com.reservation.platform.booking.application.dto.BookingResponse;
import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.global.exception.GlobalExceptionHandler;
import com.reservation.platform.global.idempotency.IdempotencyInterceptor;
import com.reservation.platform.global.idempotency.IdempotencyResponseAdvice;
import com.reservation.platform.global.idempotency.IdempotencyStore;
import com.reservation.platform.stock.exception.StockErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingApiIntegrationTest {

    private MockMvc mockMvc;
    private BookingFacade bookingFacade;
    private StringRedisTemplate redisTemplate;
    private IdempotencyStore idempotencyStore;
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        bookingFacade    = mock(BookingFacade.class);
        redisTemplate    = mock(StringRedisTemplate.class);
        idempotencyStore = mock(IdempotencyStore.class);
        valueOps         = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new BookingController(bookingFacade))
                .setControllerAdvice(
                        new GlobalExceptionHandler(),
                        new IdempotencyResponseAdvice(idempotencyStore)
                )
                .addInterceptors(new IdempotencyInterceptor(redisTemplate))
                .setValidator(validator)
                .build();
    }

    // ── X-Idempotency-Key 누락 ────────────────────────────────────────────────

    @Test
    void book_returnsBadRequestWhenIdempotencyKeyMissing() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("C007")));

        verifyNoInteractions(bookingFacade);
    }

    @Test
    void book_returnsBadRequestWhenIdempotencyKeyIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", "1")
                        .header("X-Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("C007")));

        verifyNoInteractions(bookingFacade);
    }

    // ── 정상 예약 → 성공 응답 + 캐시 저장 ──────────────────────────────────────

    @Test
    void book_returnsSuccessAndCachesResponseOnValidRequest() throws Exception {
        markAsNew("key-1");
        when(bookingFacade.book(any(), eq(1L)))
                .thenReturn(successResponse());

        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", "1")
                        .header("X-Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.bookingNumber", is("BOOK-ABCD1234")))
                .andExpect(jsonPath("$.data.orderStatus", is("CONFIRMED")))
                .andExpect(jsonPath("$.data.totalAmount", is(10_000)));

        verify(idempotencyStore).save(eq("key-1"), any());
        verify(idempotencyStore, never()).delete(anyString());
    }

    // ── 비즈니스 에러 → 실패 응답 + PROCESSING key 삭제 ────────────────────────

    @Test
    void book_deletesProcessingKeyOnStockExhausted() throws Exception {
        markAsNew("key-2");
        when(bookingFacade.book(any(), eq(1L)))
                .thenThrow(new ApplicationException(StockErrorCode.STOCK_EXHAUSTED));

        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", "1")
                        .header("X-Idempotency-Key", "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("S001")));

        verify(idempotencyStore).delete("key-2");
        verify(idempotencyStore, never()).save(anyString(), any());
    }

    // ── @Valid 검증 실패 → 400 + Facade 호출 없음 ──────────────────────────────

    @Test
    void book_returnsBadRequestWhenOrderTokenIsBlank() throws Exception {
        markAsNew("key-3");

        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", "1")
                        .header("X-Idempotency-Key", "key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1,
                                  "orderToken": "",
                                  "primaryMethod": "CREDIT_CARD",
                                  "usePoint": false,
                                  "pointAmount": 0,
                                  "cardAmount": 10000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("C001")));

        verifyNoInteractions(bookingFacade);
        // validation 실패도 PROCESSING key 삭제
        verify(idempotencyStore).delete("key-3");
    }

    @Test
    void book_returnsBadRequestWhenProductIdIsNull() throws Exception {
        markAsNew("key-4");

        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", "1")
                        .header("X-Idempotency-Key", "key-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderToken": "ORDER-1",
                                  "primaryMethod": "CREDIT_CARD",
                                  "usePoint": false,
                                  "pointAmount": 0,
                                  "cardAmount": 10000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("C001")));

        verifyNoInteractions(bookingFacade);
    }

    @Test
    void book_returnsBadRequestOnMalformedJson() throws Exception {
        markAsNew("key-5");

        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", "1")
                        .header("X-Idempotency-Key", "key-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("C002")));

        verifyNoInteractions(bookingFacade);
    }

    // ── 이미 처리 중인 요청 → 409 ─────────────────────────────────────────────

    @Test
    void book_returnsConflictWhenRequestIsProcessing() throws Exception {
        when(valueOps.get("idempotency:key-6")).thenReturn("PROCESSING");

        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", "1")
                        .header("X-Idempotency-Key", "key-6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("C005")));

        verifyNoInteractions(bookingFacade);
    }

    // ── 이미 완료된 요청 → 캐시 응답 반환 ────────────────────────────────────────

    @Test
    void book_returnsCachedResponseWhenAlreadyCompleted() throws Exception {
        String cached = """
                {"success":true,"code":"SUCCESS","message":"요청이 성공적으로 처리되었습니다.",
                "data":{"orderId":1,"orderToken":"ORDER-1","bookingNumber":"BOOK-ABCD1234",
                "orderStatus":"CONFIRMED","totalAmount":10000,"cardAmount":10000,"pointAmount":0,
                "createdAt":"2026-05-03T00:00:00"}}
                """;
        when(valueOps.get("idempotency:key-7")).thenReturn(cached);

        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", "1")
                        .header("X-Idempotency-Key", "key-7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingNumber", notNullValue()));

        verifyNoInteractions(bookingFacade);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private void markAsNew(String key) {
        when(valueOps.get("idempotency:" + key)).thenReturn(null);
        when(valueOps.setIfAbsent(
                eq("idempotency:" + key),
                eq("PROCESSING"),
                eq(300L),
                eq(TimeUnit.SECONDS)
        )).thenReturn(true);
    }

    private String validRequest() {
        return """
                {
                  "productId": 1,
                  "orderToken": "ORDER-1",
                  "primaryMethod": "CREDIT_CARD",
                  "usePoint": false,
                  "pointAmount": 0,
                  "cardAmount": 10000
                }
                """;
    }

    private BookingResponse successResponse() {
        return new BookingResponse(
                1L, "ORDER-1", "BOOK-ABCD1234", "CONFIRMED",
                10_000, 10_000, 0,
                LocalDateTime.of(2026, 5, 3, 0, 0)
        );
    }
}