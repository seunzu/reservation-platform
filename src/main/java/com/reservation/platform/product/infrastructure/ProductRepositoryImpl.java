package com.reservation.platform.product.infrastructure;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.product.domain.Product;
import com.reservation.platform.product.domain.repository.ProductRepository;
import com.reservation.platform.product.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product findById(Long id) {
        return productJpaRepository.findById(id)
                .orElseThrow(() -> new ApplicationException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}