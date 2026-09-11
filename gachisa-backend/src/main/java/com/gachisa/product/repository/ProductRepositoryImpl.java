package com.gachisa.product.repository;

import static com.gachisa.product.entity.QProduct.product;

import com.gachisa.product.entity.Product;
import com.gachisa.product.entity.ProductStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Product> search(Long sellerId, Long categoryId, Integer minPrice, Integer maxPrice, String keyword) {
        return queryFactory
            .selectFrom(product)
            .where(
                sellerEq(sellerId),
                onSaleOnlyForPublicSearch(sellerId),
                categoryEq(categoryId),
                priceGoe(minPrice),
                priceLoe(maxPrice),
                keywordContains(keyword)
            )
            .fetch();
    }

    private BooleanExpression sellerEq(Long sellerId) {
        return sellerId != null ? product.seller.id.eq(sellerId) : null;
    }

    // 공개 검색(sellerId 없음)은 판매중인 상품만 노출한다. "내 상품 관리"(sellerId 있음)는
    // 판매자가 자기 재고 전체를 관리해야 하므로 판매중지 상품도 검색에 포함시킨다.
    private BooleanExpression onSaleOnlyForPublicSearch(Long sellerId) {
        return sellerId == null ? product.status.eq(ProductStatus.ON_SALE) : null;
    }

    private BooleanExpression categoryEq(Long categoryId) {
        return categoryId != null ? product.category.id.eq(categoryId) : null;
    }

    private BooleanExpression priceGoe(Integer minPrice) {
        return minPrice != null ? product.basePrice.goe(minPrice) : null;
    }

    private BooleanExpression priceLoe(Integer maxPrice) {
        return maxPrice != null ? product.basePrice.loe(maxPrice) : null;
    }

    private BooleanExpression keywordContains(String keyword) {
        return (keyword != null && !keyword.isBlank()) ? product.name.containsIgnoreCase(keyword) : null;
    }
}
