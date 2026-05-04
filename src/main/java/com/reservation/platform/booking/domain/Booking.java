package com.reservation.platform.booking.domain;

import com.reservation.platform.order.domain.Order;
import com.reservation.platform.product.domain.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, unique = true)
    private String bookingNumber;

    @Column(nullable = false)
    private LocalDateTime checkInAt;

    @Column(nullable = false)
    private LocalDateTime checkOutAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static Booking create(Order order, Product product) {
        Booking booking = new Booking();
        booking.orderId = order.getId();
        booking.bookingNumber = generateBookingNumber();
        booking.checkInAt = product.getCheckInAt();
        booking.checkOutAt = product.getCheckOutAt();
        booking.createdAt = LocalDateTime.now();
        return booking;
    }

    private static String generateBookingNumber() {
        return "BOOK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

}
