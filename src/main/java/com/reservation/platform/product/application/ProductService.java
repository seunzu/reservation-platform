package com.reservation.platform.product.application;

import com.reservation.platform.global.exception.ApplicationException;
import com.reservation.platform.product.domain.Product;
import com.reservation.platform.product.domain.repository.ProductRepository;
import com.reservation.platform.product.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public Product getProduct(Long productId) {
        Product product = productRepository.findById(productId);
        if (!product.isSaleOpen()) {
            throw new ApplicationException(ProductErrorCode.SALE_NOT_OPENED);
        }
        return product;
    }
}
