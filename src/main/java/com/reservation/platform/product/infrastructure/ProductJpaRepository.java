package com.reservation.platform.product.infrastructure;

import com.reservation.platform.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
}
