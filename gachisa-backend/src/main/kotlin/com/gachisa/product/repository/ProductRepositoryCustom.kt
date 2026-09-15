package com.gachisa.product.repository

import com.gachisa.product.entity.Product

interface ProductRepositoryCustom {

    // sellerId가 null이면 공개 검색(판매중 상품만), sellerId가 있으면 그 판매자 소유 상품만
    // (판매중지 포함 전체 상태) 대상으로 검색한다 - "내 상품 관리" 화면에서 사용.
    fun search(sellerId: Long?, categoryId: Long?, minPrice: Int?, maxPrice: Int?, keyword: String?): List<Product>
}
