package com.gachisa.product.repository;

import com.gachisa.product.entity.Product;
import java.util.List;

public interface ProductRepositoryCustom {

    // sellerId가 null이면 공개 검색(판매중 상품만), sellerId가 있으면 그 판매자 소유 상품만
    // (판매중지 포함 전체 상태) 대상으로 검색한다 - "내 상품 관리" 화면에서 사용.
    List<Product> search(Long sellerId, Long categoryId, Integer minPrice, Integer maxPrice, String keyword);
}
