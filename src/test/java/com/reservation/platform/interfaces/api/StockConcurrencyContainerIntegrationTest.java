package com.reservation.platform.interfaces.api;

import com.reservation.platform.order.domain.OrderStatus;
import com.reservation.platform.product.domain.Product;
import com.reservation.platform.stock.domain.Stock;
import com.reservation.platform.support.AbstractContainerIntegrationTest;
import com.reservation.platform.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.reservation.platform.support.IntegrationTestFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class StockConcurrencyContainerIntegrationTest extends AbstractContainerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void concurrentBookings_doNotOversellStock() throws Exception {
        int initialStock = 3;
        int requestCount = 20;
        Product product = productJpaRepository.saveAndFlush(product("stock-concurrency", 10_000));
        Stock stock = stockJpaRepository.saveAndFlush(stock(product.getId(), initialStock));

        List<User> users = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            User user = userJpaRepository.saveAndFlush(user("stock-" + i));
            pointJpaRepository.saveAndFlush(point(user.getId(), 0));
            users.add(user);
        }

        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            int index = i;
            Callable<Integer> task = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/v1/bookings")
                                .param("userId", String.valueOf(users.get(index).getId()))
                                .header("X-Idempotency-Key", "stock-key-" + index)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cardOnlyRequest(product.getId(), "ORDER-STOCK-" + index)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            futures.add(executor.submit(task));
        }

        ready.await();
        start.countDown();

        int successCount = 0;
        int conflictCount = 0;
        for (Future<Integer> future : futures) {
            int status = future.get();
            if (status == 200) {
                successCount++;
            } else if (status == 409) {
                conflictCount++;
            }
        }
        executor.shutdown();

        assertEquals(initialStock, successCount);
        assertEquals(requestCount - initialStock, conflictCount);
        assertEquals(initialStock, bookingJpaRepository.count());
        assertEquals(initialStock, orderJpaRepository.findAll().stream()
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED)
                .count());

        entityManager.clear();
        Stock dbStock = stockJpaRepository.findById(stock.getId()).orElseThrow();
        assertEquals(0, dbStock.getRemaining());
        assertTrue(Integer.parseInt(redisTemplate.opsForValue()
                .get("stock:product:" + product.getId())) >= 0);
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
