package com.reservation.platform.booking.application.dto;

import com.reservation.platform.booking.domain.Booking;
import com.reservation.platform.order.domain.Order;
import com.reservation.platform.payment.domain.Payment;

import java.time.LocalDateTime;

public record BookingResponse(
        Long orderId,
        String orderToken,
        String reservationNumber,
        String orderStatus,
        int totalAmount,
        int cardAmount,
        int pointAmount,
        LocalDateTime createdAt
) {
    public static BookingResponse of(Order order, Booking booking, Payment payment) {
        return new BookingResponse(
                order.getId(),
                order.getOrderToken(),
                booking.getReservationNumber(),
                order.getStatus().name(),
                payment.getTotalAmount(),
                payment.getCardAmount(),
                payment.getPointAmount(),
                order.getCreatedAt()
        );
    }
}