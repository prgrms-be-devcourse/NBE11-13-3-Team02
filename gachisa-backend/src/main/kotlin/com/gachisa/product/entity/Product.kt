package com.gachisa.product.entity

import com.gachisa.category.entity.Category
import com.gachisa.user.entity.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "product")
class Product private constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    val seller: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    var category: Category,

    @Column(nullable = false)
    var name: String,

    @Lob
    var description: String?,

    @Column(nullable = false)
    var basePrice: Int,

    @Column(nullable = false)
    var stock: Int,

    var imageUrl: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProductStatus,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun stopSale() {
        this.status = ProductStatus.SUSPENDED
    }

    fun resumeSale() {
        this.status = ProductStatus.ON_SALE
    }

    fun isOwnedBy(userId: Long): Boolean = this.seller.id == userId

    fun updateName(name: String) {
        this.name = name
    }

    fun updateDescription(description: String?) {
        this.description = description
    }

    fun updateBasePrice(basePrice: Int) {
        this.basePrice = basePrice
    }

    fun updateStock(stock: Int) {
        this.stock = stock
    }

    fun updateCategory(category: Category) {
        this.category = category
    }

    fun updateImageUrl(imageUrl: String?) {
        this.imageUrl = imageUrl
    }

    companion object {
        @JvmStatic
        fun of(
            seller: User,
            category: Category,
            name: String,
            description: String?,
            basePrice: Int,
            stock: Int,
            imageUrl: String?,
            status: ProductStatus,
            createdAt: LocalDateTime,
        ): Product = Product(seller, category, name, description, basePrice, stock, imageUrl, status, createdAt)
    }
}
