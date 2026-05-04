package com.reservation.platform.booking.application;

import com.reservation.platform.booking.application.dto.BookingRequest;
import com.reservation.platform.booking.application.dto.BookingResponse;
import com.reservation.platform.booking.domain.Booking;
import com.reservation.platform.order.application.OrderService;
import com.reservation.platform.order.domain.Order;
import com.reservation.platform.payment.application.PaymentService;
import com.reservation.platform.payment.domain.Payment;
import com.reservation.platform.payment.domain.PaymentStatus;
import com.reservation.platform.point.application.PointService;
import com.reservation.platform.product.domain.Product;
import com.reservation.platform.stock.application.StockReservation;
import com.reservation.platform.stock.application.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingSagaService {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PointService pointService;
    private final BookingService bookingService;
    private final StockService stockService;

    public BookingResponse processBooking(BookingRequest request, Long userId, Product product,
                                          StockReservation stockReservation) {
        Order order = null;
        Payment payment = null;
        boolean pointUsed = false;
        StockReservation confirmedStock = stockReservation;

        try {
            order = orderService.create(
                    userId, request.productId(),
                    request.orderToken(), product.getPrice()
            );

            confirmedStock = stockService.confirm(stockReservation);
            payment = paymentService.create(order, request);
            payment = paymentService.approve(payment, request);

            if (request.usePoint() && request.pointAmount() > 0) {
                pointService.use(userId, order.getId(), request.pointAmount());
                pointUsed = true;
            }

            Booking booking = bookingService.create(order, product);
            orderService.confirm(order.getId());

            log.info("[BookingSaga] 예약 완료 orderId={}", order.getId());
            return BookingResponse.of(order, booking, payment);

        } catch (RuntimeException e) {
            compensate(request, userId, order, payment, pointUsed, confirmedStock);
            log.warn("[BookingSaga] 예약 실패, 재고 복구 productId={}", request.productId());
            throw e;
        }
    }

    private void compensate(BookingRequest request, Long userId, Order order, Payment payment,
                            boolean pointUsed, StockReservation stockReservation) {
        try {
            if (payment != null && payment.getCardAmount() > 0
                    && payment.getStatus() == PaymentStatus.COMPLETED) {
                paymentService.cancel(payment);
            }
        } catch (RuntimeException compensationError) {
            log.error("[BookingSaga] 결제 취소 보상 실패 orderId={}",
                    order != null ? order.getId() : null, compensationError);
        }

        try {
            if (pointUsed && order != null) {
                pointService.refund(userId, order.getId(), request.pointAmount());
            }
        } catch (RuntimeException compensationError) {
            log.error("[BookingSaga] 포인트 환불 보상 실패 orderId={}",
                    order != null ? order.getId() : null, compensationError);
        }

        try {
            if (order != null) {
                orderService.fail(order.getId());
            }
        } catch (RuntimeException compensationError) {
            log.error("[BookingSaga] 주문 실패 처리 보상 실패 orderId={}",
                    order != null ? order.getId() : null, compensationError);
        }

        try {
            stockService.release(stockReservation);
        } catch (RuntimeException compensationError) {
            log.error("[BookingSaga] 재고 복구 보상 실패 productId={}",
                    stockReservation.productId(), compensationError);
        }
    }
}
