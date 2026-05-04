package com.reservation.platform.support;

import com.reservation.platform.point.domain.Point;
import com.reservation.platform.product.domain.Product;
import com.reservation.platform.stock.domain.Stock;
import com.reservation.platform.user.domain.User;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

public final class IntegrationTestFixtures {

    private IntegrationTestFixtures() {
    }

    public static User user(String suffix) {
        User user = BeanUtils.instantiateClass(User.class);
        ReflectionTestUtils.setField(user, "name", "user-" + suffix);
        ReflectionTestUtils.setField(user, "email", "user-" + suffix + "@example.com");
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now());
        return user;
    }

    public static Product product(String suffix, int price) {
        Product product = BeanUtils.instantiateClass(Product.class);
        ReflectionTestUtils.setField(product, "name", "product-" + suffix);
        ReflectionTestUtils.setField(product, "price", price);
        ReflectionTestUtils.setField(product, "checkInAt", LocalDateTime.now().plusDays(1));
        ReflectionTestUtils.setField(product, "checkOutAt", LocalDateTime.now().plusDays(2));
        ReflectionTestUtils.setField(product, "saleOpenAt", LocalDateTime.now().minusMinutes(1));
        ReflectionTestUtils.setField(product, "createdAt", LocalDateTime.now());
        return product;
    }

    public static Stock stock(Long productId, int remaining) {
        Stock stock = BeanUtils.instantiateClass(Stock.class);
        ReflectionTestUtils.setField(stock, "productId", productId);
        ReflectionTestUtils.setField(stock, "remaining", remaining);
        ReflectionTestUtils.setField(stock, "updatedAt", LocalDateTime.now());
        return stock;
    }

    public static Point point(Long userId, long balance) {
        Point point = BeanUtils.instantiateClass(Point.class);
        ReflectionTestUtils.setField(point, "userId", userId);
        ReflectionTestUtils.setField(point, "balance", balance);
        ReflectionTestUtils.setField(point, "updatedAt", LocalDateTime.now());
        return point;
    }
}
