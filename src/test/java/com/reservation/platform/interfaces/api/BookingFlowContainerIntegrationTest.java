package com.reservation.platform.interfaces.api;

import com.reservation.platform.order.domain.OrderStatus;
import com.reservation.platform.payment.domain.PaymentStatus;
import com.reservation.platform.product.domain.Product;
import com.reservation.platform.stock.domain.Stock;
import com.reservation.platform.support.AbstractContainerIntegrationTest;
import com.reservation.platform.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.reservation.platform.support.IntegrationTestFixtures.*;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingFlowContainerIntegrationTest extends AbstractContainerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void checkout_returnsProductStockAndPointFromMySqlAndRedisFallback() throws Exception {
        User user = userJpaRepository.saveAndFlush(user("checkout"));
        Product product = productJpaRepository.saveAndFlush(product("checkout", 10_000));
        stockJpaRepository.saveAndFlush(stock(product.getId(), 5));
        pointJpaRepository.saveAndFlush(point(user.getId(), 30_000));

        mockMvc.perform(get("/api/v1/checkout")
                        .param("userId", String.valueOf(user.getId()))
                        .param("productId", String.valueOf(product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.productId", is(product.getId().intValue())))
                .andExpect(jsonPath("$.data.remainingStock", is(5)))
                .andExpect(jsonPath("$.data.availablePoint", is(30_000)));
    }

    @Test
    void book_persistsConfirmedReservationAndSideEffects() throws Exception {
        User user = userJpaRepository.saveAndFlush(user("booking"));
        Product product = productJpaRepository.saveAndFlush(product("booking", 10_000));
        stockJpaRepository.saveAndFlush(stock(product.getId(), 1));
        pointJpaRepository.saveAndFlush(point(user.getId(), 0));

        mockMvc.perform(post("/api/v1/bookings")
                        .param("userId", String.valueOf(user.getId()))
                        .header("X-Idempotency-Key", "booking-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardOnlyRequest(product.getId(), "ORDER-CONTAINER-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderStatus", is("CONFIRMED")))
                .andExpect(jsonPath("$.data.totalAmount", is(10_000)));

        assertEquals(1, orderJpaRepository.count());
        assertEquals(1, bookingJpaRepository.count());
        assertEquals(1, paymentJpaRepository.count());

        assertTrue(orderJpaRepository.findAll().stream()
                .allMatch(order -> order.getStatus() == OrderStatus.CONFIRMED));
        assertTrue(paymentJpaRepository.findAll().stream()
                .allMatch(payment -> payment.getStatus() == PaymentStatus.COMPLETED));

        Stock dbStock = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertEquals(0, dbStock.getRemaining());
        assertEquals("0", redisTemplate.opsForValue().get("stock:product:" + product.getId()));
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
