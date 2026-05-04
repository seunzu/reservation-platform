package com.reservation.platform.booking.infrastructure;

import com.reservation.platform.booking.domain.Booking;
import com.reservation.platform.booking.domain.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BookingRepositoryImpl implements BookingRepository {

    private final BookingJpaRepository bookingJpaRepository;

    @Override
    public Booking save(Booking booking) {
        return bookingJpaRepository.save(booking);
    }
}