package com.reservation.platform.booking.infrastructure;

import com.reservation.platform.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingJpaRepository extends JpaRepository<Booking, Long> {
}
