package com.reservation.platform.booking.application;

import com.reservation.platform.booking.domain.Booking;
import com.reservation.platform.booking.domain.repository.BookingRepository;
import com.reservation.platform.order.domain.Order;
import com.reservation.platform.product.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;

    public Booking create(Order order, Product product) {
        Booking booking = Booking.create(order, product);
        bookingRepository.save(booking);
        log.info("[Booking] 예약 생성 bookingId={}, reservationNumber={}",
                booking.getId(), booking.getReservationNumber());
        return booking;
    }
}
