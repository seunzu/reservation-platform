package com.reservation.platform.point.infrastructure;

import com.reservation.platform.point.domain.PointTransaction;
import com.reservation.platform.point.domain.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PointTransactionRepositoryImpl implements PointTransactionRepository {

    private final PointTransactionJpaRepository pointTransactionJpaRepository;

    @Override
    public PointTransaction save(PointTransaction pointTransaction) {
        return pointTransactionJpaRepository.save(pointTransaction);
    }
}
