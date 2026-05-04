package com.reservation.platform.stock.application;

import com.reservation.platform.stock.domain.Stock;
import com.reservation.platform.stock.domain.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockDbLockService {

    private final StockRepository stockRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decreaseWithLock(Long productId) {
        Stock stock = stockRepository.findByProductIdWithLock(productId);
        stock.decrease();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseWithLock(Long productId) {
        Stock stock = stockRepository.findByProductIdWithLock(productId);
        stock.increase();
    }
}
