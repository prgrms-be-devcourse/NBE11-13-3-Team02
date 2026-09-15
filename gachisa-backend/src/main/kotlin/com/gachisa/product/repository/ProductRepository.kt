package com.gachisa.product.repository

import com.gachisa.product.entity.Product
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, Long>, ProductRepositoryCustom {

    fun existsBySellerIdAndName(sellerId: Long, name: String): Boolean

    fun existsBySellerIdAndNameAndIdNot(sellerId: Long, name: String, id: Long): Boolean

    fun findBySellerId(sellerId: Long): List<Product>
}
