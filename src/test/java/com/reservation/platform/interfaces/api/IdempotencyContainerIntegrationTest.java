package com.reservation.platform.interfaces.api;

import com.reservation.platform.product.domain.Product;
import com.reservation.platform.support.AbstractContainerIntegrationTest;
import com.reservation.platform.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static com.reservation.platform.support.IntegrationTestFixtures.*;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyContainerIntegrationTest extends AbstractContainerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void book_returnsCachedResponseAndDoesNotCreateDuplicateOrder() throws Exception {
        User user = userJpaRepository.saveAndFlush(user("idempotency"));
        Product product = productJpaRepository.saveAndFlush(product("idempotency", 10_000));
        stockJpaRepository.saveAndFlush(stock(product.getId(), 2));
        pointJpaRepository.saveAndFlush(point(user.getId(), 0));

        String body = cardOnlyRequest(product.getId(), "ORDER-IDEMPOTENCY-1");

        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", String.valueOf(user.getId()))
                        .header("X-Idempotency-Key", "same-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", String.valueOf(user.getId()))
                        .header("X-Idempotency-Key", "same-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderToken", is("ORDER-IDEMPOTENCY-1")));

        assertEquals(1, orderJpaRepository.count());
        assertEquals(1, bookingJpaRepository.count());
        assertEquals(1, paymentJpaRepository.count());
        assertEquals(1, redisTemplate.keys("idempotency:same-key-1").size());
    }

    @Test
    void book_returnsConflictWhenSameKeyIsAlreadyProcessing() throws Exception {
        User user = userJpaRepository.saveAndFlush(user("idempotency-processing"));
        Product product = productJpaRepository.saveAndFlush(product("idempotency-processing", 10_000));
        stockJpaRepository.saveAndFlush(stock(product.getId(), 1));
        pointJpaRepository.saveAndFlush(point(user.getId(), 0));

        // PROCESSING 상태를 Redis에 직접 설정해 처리 중인 요청 시뮬레이션
        String key = "processing-test-key";
        redisTemplate.opsForValue().set("idempotency:" + key, "PROCESSING", 300, TimeUnit.SECONDS);

        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", String.valueOf(user.getId()))
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardOnlyRequest(product.getId(), "ORDER-PROC-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("C005")));

        assertEquals(0, orderJpaRepository.count());
        assertEquals(0, bookingJpaRepository.count());
    }

    private String cardOnlyRequest(Long productId, String orderToken) {
        return """
                {
                  "productId": %d,
                  "orderToken": "%s",
                  "primaryMethod": "CREDIT_CARD",
                  "usePoint": false,
                  "pointAmount": 0,
                  "cardAmount": 10000
                }
                """.formatted(productId, orderToken);
    }
}
