package com.reservation.platform.stock.infrastructure;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.stock.domain.Stock;
import com.reservation.platform.stock.domain.repository.StockRepository;
import com.reservation.platform.stock.exception.StockErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockRepositoryImpl implements StockRepository {

    private final StockJpaRepository stockJpaRepository;

    @Override
    public Stock findByProductId(Long productId) {
        return stockJpaRepository.findByProductId(productId)
                .orElseThrow(() -> new ApplicationException(StockErrorCode.STOCK_NOT_FOUND));
    }

    @Override
    public Stock findByProductIdWithLock(Long productId) {
        return stockJpaRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new ApplicationException(StockErrorCode.STOCK_NOT_FOUND));
    }
}