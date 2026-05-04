package com.reservation.platform.point.domain.repository;

import com.reservation.platform.point.domain.Point;

public interface PointRepository {

    Point findByUserId(Long userId);
    Point findByUserIdWithLock(Long userId);
}
