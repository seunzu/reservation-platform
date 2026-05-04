package com.reservation.platform.booking.application.dto;

import com.reservation.platform.payment.domain.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record BookingRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,

        @NotBlank(message = "주문 토큰은 필수입니다.")
        String orderToken,

        @NotNull(message = "결제 수단은 필수입니다.")
        PaymentMethod primaryMethod,

        boolean usePoint,

        @PositiveOrZero(message = "포인트 금액은 0 이상이어야 합니다.")
        int pointAmount,

        @PositiveOrZero(message = "카드 금액은 0 이상이어야 합니다.")
        int cardAmount
) {}

