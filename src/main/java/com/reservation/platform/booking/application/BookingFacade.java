package com.reservation.platform.booking.application;

import com.reservation.platform.booking.application.dto.CheckoutResponse;
import com.reservation.platform.point.application.PointService;
import com.reservation.platform.product.application.ProductService;
import com.reservation.platform.product.domain.Product;
import com.reservation.platform.stock.application.StockService;
import com.reservation.platform.user.application.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingFacade {

    private final ProductService productService;
    private final StockService stockService;
    private final UserService userService;
    private final PointService pointService;

    public CheckoutResponse checkout(Long productId, Long userId) {
        userService.getUser(userId);
        Product product = productService.getProduct(productId);
        int remaining = stockService.getRemaining(productId);
        long availablePoint = pointService.getAvailablePoint(userId);

        return CheckoutResponse.of(product, remaining, availablePoint);
    }
}
