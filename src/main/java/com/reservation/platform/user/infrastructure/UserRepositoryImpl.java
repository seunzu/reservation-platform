package com.reservation.platform.user.infrastructure;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.user.domain.User;
import com.reservation.platform.user.domain.repository.UserRepository;
import com.reservation.platform.user.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User findById(Long id) {
        return userJpaRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(UserErrorCode.USER_NOT_FOUND));
    }
}