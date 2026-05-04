package com.reservation.platform.point.application;

import com.reservation.platform.point.domain.repository.PointRepository;
import com.reservation.platform.point.domain.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointService {

    private final PointRepository pointRepository;
    private final PointTransactionRepository pointTransactionRepository;

    public long getAvailablePoint(Long userId) {
        return pointRepository.findByUserId(userId).getBalance();
    }
}