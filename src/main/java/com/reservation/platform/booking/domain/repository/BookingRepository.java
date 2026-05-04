package com.reservation.platform.booking.domain.repository;

import com.reservation.platform.booking.domain.Booking;

public interface BookingRepository {

    Booking save(Booking booking);
}
