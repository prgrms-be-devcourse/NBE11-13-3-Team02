package com.gachisa.product.repository

import com.gachisa.product.entity.Product
import com.gachisa.product.entity.ProductStatus
import com.gachisa.product.entity.QProduct.product
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory

class ProductRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : ProductRepositoryCustom {

    override fun search(sellerId: Long?, categoryId: Long?, minPrice: Int?, maxPrice: Int?, keyword: String?): List<Product> {
        return queryFactory
            .selectFrom(product)
            .where(
                sellerEq(sellerId),
                onSaleOnlyForPublicSearch(sellerId),
                categoryEq(categoryId),
                priceGoe(minPrice),
                priceLoe(maxPrice),
                keywordContains(keyword),
            )
            .fetch()
    }

    private fun sellerEq(sellerId: Long?): BooleanExpression? =
        sellerId?.let { product.seller.id.eq(it) }

    // 공개 검색(sellerId 없음)은 판매중인 상품만 노출한다. "내 상품 관리"(sellerId 있음)는
    // 판매자가 자기 재고 전체를 관리해야 하므로 판매중지 상품도 검색에 포함시킨다.
    private fun onSaleOnlyForPublicSearch(sellerId: Long?): BooleanExpression? =
        if (sellerId == null) product.status.eq(ProductStatus.ON_SALE) else null

    private fun categoryEq(categoryId: Long?): BooleanExpression? =
        categoryId?.let { product.category.id.eq(it) }

    private fun priceGoe(minPrice: Int?): BooleanExpression? =
        minPrice?.let { product.basePrice.goe(it) }

    private fun priceLoe(maxPrice: Int?): BooleanExpression? =
        maxPrice?.let { product.basePrice.loe(it) }

    private fun keywordContains(keyword: String?): BooleanExpression? =
        if (!keyword.isNullOrBlank()) product.name.containsIgnoreCase(keyword) else null
}
