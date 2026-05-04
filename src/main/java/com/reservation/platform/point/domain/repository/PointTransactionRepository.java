package com.reservation.platform.point.domain.repository;

import com.reservation.platform.point.domain.PointTransaction;

public interface PointTransactionRepository {

    PointTransaction save(PointTransaction pointTransaction);
}