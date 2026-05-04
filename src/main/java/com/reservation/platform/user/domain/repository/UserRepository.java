package com.reservation.platform.user.domain.repository;

import com.reservation.platform.user.domain.User;

public interface UserRepository {

    User findById(Long id);
}
