package com.gachisa.product.dto

import com.gachisa.product.entity.Product
import java.time.LocalDateTime

data class ProductResponse(
    val id: Long?,
    val sellerId: Long,
    val sellerName: String,
    val categoryId: Long,
    val categoryName: String,
    val name: String,
    val description: String?,
    val basePrice: Int,
    val stock: Int,
    val imageUrl: String?,
    val status: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        @JvmStatic
        fun of(product: Product): ProductResponse {
            return ProductResponse(
                id = product.id,
                sellerId = product.seller.id!!,
                sellerName = product.seller.name,
                categoryId = product.category.id!!,
                categoryName = product.category.name,
                name = product.name,
                description = product.description,
                basePrice = product.basePrice,
                stock = product.stock,
                imageUrl = product.imageUrl,
                status = product.status.name,
                createdAt = product.createdAt,
            )
        }
    }
}
