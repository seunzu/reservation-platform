package com.reservation.platform.point.infrastructure;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.point.domain.Point;
import com.reservation.platform.point.domain.repository.PointRepository;
import com.reservation.platform.point.exception.PointErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PointRepositoryImpl implements PointRepository {

    private final PointJpaRepository pointJpaRepository;

    @Override
    public Point findByUserId(Long userId) {
        return pointJpaRepository.findByUserId(userId)
                .orElseThrow(() -> new ApplicationException(PointErrorCode.POINT_NOT_FOUND));
    }

    @Override
    public Point findByUserIdWithLock(Long userId) {
        return pointJpaRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ApplicationException(PointErrorCode.POINT_NOT_FOUND));
    }
}
