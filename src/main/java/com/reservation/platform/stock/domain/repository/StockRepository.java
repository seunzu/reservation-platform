package com.reservation.platform.stock.domain.repository;

import com.reservation.platform.stock.domain.Stock;

public interface StockRepository {

    Stock findByProductId(Long productId);
    Stock findByProductIdWithLock(Long productId);
}