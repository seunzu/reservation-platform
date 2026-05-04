package com.reservation.platform.user.infrastructure;

import com.reservation.platform.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, Long> {
}
