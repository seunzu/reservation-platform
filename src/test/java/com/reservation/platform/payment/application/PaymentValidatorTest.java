package com.reservation.platform.payment.application;

import com.reservation.platform.booking.application.dto.BookingRequest;
import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.payment.domain.PaymentMethod;
import com.reservation.platform.product.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentValidatorTest {

    private final PaymentValidator paymentValidator = new PaymentValidator();

    // ── 정상 케이스 ────────────────────────────────────────────────────────────

    @Test
    void validate_allowsCreditCardOnly() {
        // 신용카드 단독 결제 (포인트 미사용)
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.CREDIT_CARD,
                false, 0, 10_000
        );
        assertDoesNotThrow(() -> paymentValidator.validate(request, product(10_000)));
    }

    @Test
    void validate_allowsCreditCardWithPoint() {
        // 신용카드 + 포인트 복합 결제
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.CREDIT_CARD,
                true, 2_000, 8_000
        );
        assertDoesNotThrow(() -> paymentValidator.validate(request, product(10_000)));
    }

    @Test
    void validate_allowsYPayOnly() {
        // Y페이 단독 결제
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.Y_PAY,
                false, 0, 10_000
        );
        assertDoesNotThrow(() -> paymentValidator.validate(request, product(10_000)));
    }

    @Test
    void validate_allowsYPayWithPoint() {
        // Y페이 + 포인트 복합 결제
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.Y_PAY,
                true, 2_000, 8_000
        );
        assertDoesNotThrow(() -> paymentValidator.validate(request, product(10_000)));
    }

    @Test
    void validate_allowsPointOnlyPayment() {
        // 포인트 단독 결제
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.POINT,
                true, 10_000, 0
        );
        assertDoesNotThrow(() -> paymentValidator.validate(request, product(10_000)));
    }

    // ── 금액 불일치 ─────────────────────────────────────────────────────────────

    @Test
    void validate_rejectsWhenTotalAmountDoesNotMatchProductPrice() {
        // card(8000) + point(1000) = 9000 ≠ product(10000)
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.CREDIT_CARD,
                true, 1_000, 8_000
        );
        assertThrows(ApplicationException.class,
                () -> paymentValidator.validate(request, product(10_000)));
    }

    @Test
    void validate_rejectsWhenTotalIsZero() {
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.CREDIT_CARD,
                false, 0, 0
        );
        assertThrows(ApplicationException.class,
                () -> paymentValidator.validate(request, product(10_000)));
    }

    // ── 주 결제 금액 오류 ────────────────────────────────────────────────────────

    @Test
    void validate_rejectsCreditCardWithZeroCardAmount() {
        // CREDIT_CARD인데 cardAmount = 0
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.CREDIT_CARD,
                true, 10_000, 0
        );
        assertThrows(ApplicationException.class,
                () -> paymentValidator.validate(request, product(10_000)));
    }

    @Test
    void validate_rejectsYPayWithZeroCardAmount() {
        // Y_PAY인데 cardAmount = 0
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.Y_PAY,
                true, 10_000, 0
        );
        assertThrows(ApplicationException.class,
                () -> paymentValidator.validate(request, product(10_000)));
    }

    @Test
    void validate_rejectsPointMethodWithNonZeroCardAmount() {
        // POINT 결제인데 cardAmount > 0
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.POINT,
                true, 5_000, 5_000
        );
        assertThrows(ApplicationException.class,
                () -> paymentValidator.validate(request, product(10_000)));
    }

    // ── 포인트 사용 플래그 오류 ──────────────────────────────────────────────────

    @Test
    void validate_rejectsPointAmountWhenUsePointIsFalse() {
        // usePoint=false인데 pointAmount > 0
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.CREDIT_CARD,
                false, 1_000, 9_000
        );
        assertThrows(ApplicationException.class,
                () -> paymentValidator.validate(request, product(10_000)));
    }

    @Test
    void validate_rejectsUsePointTrueWithZeroPointAmount() {
        // usePoint=true인데 pointAmount = 0
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.CREDIT_CARD,
                true, 0, 10_000
        );
        assertThrows(ApplicationException.class,
                () -> paymentValidator.validate(request, product(10_000)));
    }

    @Test
    void validate_rejectsPointMethodWithoutUsePointFlag() {
        // primaryMethod=POINT인데 usePoint=false
        BookingRequest request = new BookingRequest(
                1L, "order-1", PaymentMethod.POINT,
                false, 10_000, 0
        );
        assertThrows(ApplicationException.class,
                () -> paymentValidator.validate(request, product(10_000)));
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private Product product(int price) {
        Product product = BeanUtils.instantiateClass(Product.class);
        ReflectionTestUtils.setField(product, "price", price);
        return product;
    }
}