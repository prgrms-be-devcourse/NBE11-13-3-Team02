package com.gachisa.product.repository;

import com.gachisa.product.entity.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    boolean existsBySellerIdAndName(Long sellerId, String name);

    boolean existsBySellerIdAndNameAndIdNot(Long sellerId, String name, Long id);

    List<Product> findBySellerId(Long sellerId);
}
