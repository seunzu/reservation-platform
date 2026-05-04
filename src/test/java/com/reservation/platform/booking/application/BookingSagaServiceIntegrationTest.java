package com.reservation.platform.booking.application;

import com.reservation.platform.booking.application.dto.BookingRequest;
import com.reservation.platform.booking.domain.Booking;
import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.order.application.OrderService;
import com.reservation.platform.order.domain.Order;
import com.reservation.platform.order.domain.OrderStatus;
import com.reservation.platform.payment.application.PaymentService;
import com.reservation.platform.payment.domain.Payment;
import com.reservation.platform.payment.domain.PaymentMethod;
import com.reservation.platform.payment.exception.PaymentErrorCode;
import com.reservation.platform.point.application.PointService;
import com.reservation.platform.product.domain.Product;
import com.reservation.platform.stock.application.StockReservation;
import com.reservation.platform.stock.application.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingSagaServiceIntegrationTest {

    private OrderService orderService;
    private PaymentService paymentService;
    private PointService pointService;
    private BookingService bookingService;
    private StockService stockService;
    private BookingSagaService svc;

    @BeforeEach
    void setUp() {
        orderService   = mock(OrderService.class);
        paymentService = mock(PaymentService.class);
        pointService   = mock(PointService.class);
        bookingService = mock(BookingService.class);
        stockService   = mock(StockService.class);
        svc = new BookingSagaService(
                orderService, paymentService, pointService, bookingService, stockService);
    }

    // ── 정상 흐름 ─────────────────────────────────────────────────────────────

    @Test
    void processBooking_succeedsAndReturnsBookingResponse() {
        BookingRequest request = requestWithPoint();
        Order order            = pendingOrder();
        Payment pending        = pendingPayment();
        Payment completed      = completedPayment();
        Product product        = product();
        Booking booking        = booking(order);
        StockReservation rsv   = StockReservation.redisReserved(1L);
        StockReservation conf  = rsv.confirmDb();

        when(orderService.create(1L, 1L, "ORDER-1", 10_000)).thenReturn(order);
        when(stockService.confirm(rsv)).thenReturn(conf);
        when(paymentService.create(order, request)).thenReturn(pending);
        when(paymentService.approve(pending, request)).thenReturn(completed);
        when(bookingService.create(order, product)).thenReturn(booking);

        var response = svc.processBooking(request, 1L, product, rsv);

        assertNotNull(response);
        verify(orderService).confirm(order.getId());
        verify(pointService).use(1L, order.getId(), 2_000);
        verify(paymentService, never()).cancel(any());
        verify(stockService, never()).release(any());
    }

    @Test
    void processBooking_succeedsWithoutPointUsage() {
        BookingRequest request = requestCardOnly();
        Order order            = pendingOrder();
        Payment completed      = completedPayment();
        Product product        = product();
        Booking booking        = booking(order);
        StockReservation rsv   = StockReservation.redisReserved(1L);
        StockReservation conf  = rsv.confirmDb();

        when(orderService.create(1L, 1L, "ORDER-1", 10_000)).thenReturn(order);
        when(stockService.confirm(rsv)).thenReturn(conf);
        when(paymentService.create(order, request)).thenReturn(pendingPayment());
        when(paymentService.approve(any(Payment.class), eq(request))).thenReturn(completed);
        when(bookingService.create(order, product)).thenReturn(booking);

        svc.processBooking(request, 1L, product, rsv);

        verify(pointService, never()).use(anyLong(), anyLong(), anyInt());
        verify(orderService).confirm(order.getId());
    }

    // ── PG 승인 실패 ───────────────────────────────────────────────────────────

    @Test
    void processBooking_failsOrderAndReleasesStockWhenPaymentApproveFails() {
        BookingRequest request = requestWithPoint();
        Order order            = pendingOrder();
        Payment pending        = pendingPayment();
        StockReservation rsv   = StockReservation.redisReserved(1L);
        StockReservation conf  = rsv.confirmDb();

        when(orderService.create(1L, 1L, "ORDER-1", 10_000)).thenReturn(order);
        when(stockService.confirm(rsv)).thenReturn(conf);
        when(paymentService.create(order, request)).thenReturn(pending);
        when(paymentService.approve(pending, request))
                .thenThrow(new ApplicationException(PaymentErrorCode.PAYMENT_FAILED));

        assertThrows(ApplicationException.class,
                () -> svc.processBooking(request, 1L, product(), rsv));

        // PG 승인 전에 던졌으므로 결제 취소 불필요, 포인트 미차감이므로 환불 불필요
        verify(paymentService, never()).cancel(any());
        verify(pointService, never()).refund(anyLong(), anyLong(), anyInt());
        verify(orderService).fail(order.getId());
        verify(stockService).release(conf);
    }

    // ── 예약 생성 실패 (PG 승인 이후) ──────────────────────────────────────────

    @Test
    void processBooking_compensatesFullyWhenBookingCreateFails() {
        BookingRequest request = requestWithPoint();
        Order order            = pendingOrder();
        Payment completed      = completedPayment();
        StockReservation rsv   = StockReservation.redisReserved(1L);
        StockReservation conf  = rsv.confirmDb();

        when(orderService.create(1L, 1L, "ORDER-1", 10_000)).thenReturn(order);
        when(stockService.confirm(rsv)).thenReturn(conf);
        when(paymentService.create(order, request)).thenReturn(pendingPayment());
        when(paymentService.approve(any(Payment.class), eq(request))).thenReturn(completed);
        // 포인트 차감 성공 후 booking 생성 실패 — any(Product.class)로 인스턴스 동등성 우회
        doThrow(new RuntimeException("booking DB error"))
                .when(bookingService).create(eq(order), any(Product.class));

        assertThrows(RuntimeException.class,
                () -> svc.processBooking(request, 1L, product(), rsv));

        verify(paymentService).cancel(completed);
        verify(pointService).refund(1L, order.getId(), 2_000);
        verify(orderService).fail(order.getId());
        verify(stockService).release(conf);
    }

    @Test
    void processBooking_compensatesWithoutPointRefundWhenPointNotUsed() {
        // usePoint=false 상태에서 booking 생성 실패
        BookingRequest request = requestCardOnly();
        Order order            = pendingOrder();
        Payment completed      = completedPayment();
        StockReservation rsv   = StockReservation.redisReserved(1L);
        StockReservation conf  = rsv.confirmDb();

        when(orderService.create(1L, 1L, "ORDER-1", 10_000)).thenReturn(order);
        when(stockService.confirm(rsv)).thenReturn(conf);
        when(paymentService.create(order, request)).thenReturn(pendingPayment());
        when(paymentService.approve(any(Payment.class), eq(request))).thenReturn(completed);
        doThrow(new RuntimeException("booking DB error"))
                .when(bookingService).create(eq(order), any(Product.class));

        assertThrows(RuntimeException.class,
                () -> svc.processBooking(request, 1L, product(), rsv));

        verify(paymentService).cancel(completed);
        verify(pointService, never()).refund(anyLong(), anyLong(), anyInt()); // 포인트 미사용
        verify(orderService).fail(order.getId());
        verify(stockService).release(conf);
    }

    // ── 주문 생성 실패 ─────────────────────────────────────────────────────────

    @Test
    void processBooking_releasesStockEvenWhenOrderCreateFails() {
        BookingRequest request = requestWithPoint();
        StockReservation rsv   = StockReservation.redisReserved(1L);

        when(orderService.create(anyLong(), anyLong(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("duplicate order"));

        assertThrows(RuntimeException.class,
                () -> svc.processBooking(request, 1L, product(), rsv));

        // order가 null이므로 fail 호출 없음
        verify(orderService, never()).fail(anyLong());
        // stockReservation은 reserve 단계의 것으로 release
        verify(stockService).release(rsv);
    }

    // ── 보상 자체 실패 → 다음 보상은 계속 진행 ──────────────────────────────────

    @Test
    void processBooking_continuesCompensationEvenWhenPaymentCancelFails() {
        BookingRequest request = requestWithPoint();
        Order order            = pendingOrder();
        Payment completed      = completedPayment();
        StockReservation rsv   = StockReservation.redisReserved(1L);
        StockReservation conf  = rsv.confirmDb();

        when(orderService.create(1L, 1L, "ORDER-1", 10_000)).thenReturn(order);
        when(stockService.confirm(rsv)).thenReturn(conf);
        when(paymentService.create(order, request)).thenReturn(pendingPayment());
        when(paymentService.approve(any(Payment.class), eq(request))).thenReturn(completed);
        doThrow(new RuntimeException("booking DB error"))
                .when(bookingService).create(eq(order), any(Product.class));
        // PG 취소도 실패
        doThrow(new RuntimeException("PG cancel timeout"))
                .when(paymentService).cancel(any());

        // 보상 실패해도 원본 예외가 전파되어야 함
        assertThrows(RuntimeException.class,
                () -> svc.processBooking(request, 1L, product(), rsv));

        // PG 취소 실패해도 이후 보상(포인트 환불, 주문 실패, 재고 복구)은 실행됨
        verify(pointService).refund(1L, order.getId(), 2_000);
        verify(orderService).fail(order.getId());
        verify(stockService).release(conf);
    }

    // ── 픽스처 ────────────────────────────────────────────────────────────────

    private BookingRequest requestWithPoint() {
        return new BookingRequest(
                1L, "ORDER-1", PaymentMethod.CREDIT_CARD,
                true, 2_000, 8_000
        );
    }

    private BookingRequest requestCardOnly() {
        return new BookingRequest(
                1L, "ORDER-1", PaymentMethod.CREDIT_CARD,
                false, 0, 10_000
        );
    }

    private Product product() {
        Product p = BeanUtils.instantiateClass(Product.class);
        ReflectionTestUtils.setField(p, "price", 10_000);
        ReflectionTestUtils.setField(p, "checkInAt",  LocalDateTime.of(2026, 5, 3, 15, 0));
        ReflectionTestUtils.setField(p, "checkOutAt", LocalDateTime.of(2026, 5, 4, 11, 0));
        return p;
    }

    private Order pendingOrder() {
        Order o = BeanUtils.instantiateClass(Order.class);
        ReflectionTestUtils.setField(o, "id",          10L);
        ReflectionTestUtils.setField(o, "userId",      1L);
        ReflectionTestUtils.setField(o, "productId",   1L);
        ReflectionTestUtils.setField(o, "orderToken",  "ORDER-1");
        ReflectionTestUtils.setField(o, "status",      OrderStatus.PENDING);
        ReflectionTestUtils.setField(o, "totalAmount", 10_000);
        ReflectionTestUtils.setField(o, "createdAt",   LocalDateTime.now());
        return o;
    }

    private Payment pendingPayment() {
        return Payment.create(10L, PaymentMethod.CREDIT_CARD, 8_000, 2_000);
    }

    private Payment completedPayment() {
        Payment p = pendingPayment();
        p.complete("PG-1234567890AB");
        return p;
    }

    private Booking booking(Order order) {
        Booking b = BeanUtils.instantiateClass(Booking.class);
        ReflectionTestUtils.setField(b, "id",               1L);
        ReflectionTestUtils.setField(b, "orderId",          order.getId());
        ReflectionTestUtils.setField(b, "bookingNumber","BOOK-ABCD1234");
        ReflectionTestUtils.setField(b, "checkInAt",        LocalDateTime.of(2026, 5, 3, 15, 0));
        ReflectionTestUtils.setField(b, "checkOutAt",       LocalDateTime.of(2026, 5, 4, 11, 0));
        ReflectionTestUtils.setField(b, "createdAt",        LocalDateTime.now());
        return b;
    }
}
