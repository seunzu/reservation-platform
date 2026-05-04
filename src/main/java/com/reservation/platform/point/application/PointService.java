package com.reservation.platform.point.application;

import com.reservation.platform.point.domain.Point;
import com.reservation.platform.point.domain.PointTransaction;
import com.reservation.platform.point.domain.repository.PointRepository;
import com.reservation.platform.point.domain.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointService {

    private final PointRepository pointRepository;
    private final PointTransactionRepository pointTransactionRepository;

    public long getAvailablePoint(Long userId) {
        return pointRepository.findByUserId(userId).getBalance();
    }

    @Transactional
    public void use(Long userId, Long orderId, int amount) {
        if (amount <= 0) return;

        Point point = pointRepository.findByUserIdWithLock(userId);
        point.use(amount);

        PointTransaction tx = PointTransaction.use(point, orderId, amount);
        pointTransactionRepository.save(tx);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refund(Long userId, Long orderId, int amount) {
        if (amount <= 0) return;

        Point point = pointRepository.findByUserIdWithLock(userId);
        point.refund(amount);

        PointTransaction tx = PointTransaction.refund(point, orderId, amount);
        pointTransactionRepository.save(tx);
    }
}