package com.reservation.platform.point.infrastructure;

import com.reservation.platform.point.domain.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionJpaRepository extends JpaRepository<PointTransaction, Long> {
}
