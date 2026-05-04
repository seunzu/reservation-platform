package com.reservation.platform.product.domain.repository;

import com.reservation.platform.product.domain.Product;

public interface ProductRepository {

    Product findById(Long id);
}