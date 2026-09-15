package com.gachisa.product.service

import com.gachisa.category.repository.CategoryRepository
import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.product.dto.ProductCreateRequest
import com.gachisa.product.dto.ProductPaymentInfo
import com.gachisa.product.dto.ProductResponse
import com.gachisa.product.dto.ProductUpdateRequest
import com.gachisa.product.entity.Product
import com.gachisa.product.entity.ProductStatus
import com.gachisa.product.repository.ProductRepository
import com.gachisa.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun createProduct(sellerId: Long, request: ProductCreateRequest, imageUrl: String?): ProductResponse {
        val category = categoryRepository.findById(request.categoryId!!)
            .orElseThrow { CustomException(ErrorCode.CATEGORY_NOT_FOUND) }

        if (productRepository.existsBySellerIdAndName(sellerId, request.name)) {
            throw CustomException(ErrorCode.PRODUCT_NAME_DUPLICATED)
        }

        val product = Product.of(
            seller = userRepository.getReferenceById(sellerId),
            category = category,
            name = request.name,
            description = request.description,
            basePrice = request.basePrice,
            stock = request.stock,
            imageUrl = imageUrl,
            status = ProductStatus.ON_SALE,
            createdAt = LocalDateTime.now(),
        )
        productRepository.save(product)

        return ProductResponse.of(product)
    }

    fun getProducts(): List<ProductResponse> {
        return productRepository.findAll().map { ProductResponse.of(it) }
    }

    /** 판매자 본인이 등록한 상품만 조회 (상품 관리 페이지용) */
    fun getMyProducts(sellerId: Long): List<ProductResponse> {
        return productRepository.findBySellerId(sellerId).map { ProductResponse.of(it) }
    }

    fun getProduct(productId: Long): ProductResponse {
        val product = getProductOrThrow(productId)
        return ProductResponse.of(product)
    }

    fun getPaymentInfo(productId: Long): ProductPaymentInfo {
        val product = getProductOrThrow(productId)
        return ProductPaymentInfo(product.id!!, product.basePrice)
    }

    fun searchProducts(categoryId: Long?, minPrice: Int?, maxPrice: Int?, keyword: String?): List<ProductResponse> {
        return productRepository.search(null, categoryId, minPrice, maxPrice, keyword).map { ProductResponse.of(it) }
    }

    /** 판매자 본인 상품 안에서만 검색 (상품 관리 페이지용) - 판매중지 상품도 포함 */
    fun searchMyProducts(
        sellerId: Long,
        categoryId: Long?,
        minPrice: Int?,
        maxPrice: Int?,
        keyword: String?,
    ): List<ProductResponse> {
        return productRepository.search(sellerId, categoryId, minPrice, maxPrice, keyword).map { ProductResponse.of(it) }
    }

    @Transactional
    fun updateProduct(productId: Long, sellerId: Long, request: ProductUpdateRequest): ProductResponse {
        val product = getProductOrThrow(productId)
        validateOwner(product, sellerId)

        if (!request.name.isNullOrBlank()) {
            if (product.name != request.name &&
                productRepository.existsBySellerIdAndNameAndIdNot(sellerId, request.name, productId)
            ) {
                throw CustomException(ErrorCode.PRODUCT_NAME_DUPLICATED)
            }
            product.updateName(request.name)
        }
        if (request.description != null) {
            product.updateDescription(request.description)
        }
        if (request.basePrice != null) {
            product.updateBasePrice(request.basePrice)
        }
        if (request.stock != null) {
            product.updateStock(request.stock)
        }
        if (request.categoryId != null) {
            val category = categoryRepository.findById(request.categoryId)
                .orElseThrow { CustomException(ErrorCode.CATEGORY_NOT_FOUND) }
            product.updateCategory(category)
        }

        return ProductResponse.of(product)
    }

    @Transactional
    fun updateProductImage(productId: Long, sellerId: Long, imageUrl: String?): ProductResponse {
        val product = getProductOrThrow(productId)
        validateOwner(product, sellerId)
        product.updateImageUrl(imageUrl)
        return ProductResponse.of(product)
    }

    @Transactional
    fun deleteProduct(productId: Long, sellerId: Long) {
        val product = getProductOrThrow(productId)
        validateOwner(product, sellerId)
        product.stopSale()
    }

    @Transactional
    fun resumeProduct(productId: Long, sellerId: Long): ProductResponse {
        val product = getProductOrThrow(productId)
        validateOwner(product, sellerId)
        product.resumeSale()
        return ProductResponse.of(product)
    }

    private fun getProductOrThrow(productId: Long): Product {
        return productRepository.findById(productId)
            .orElseThrow { CustomException(ErrorCode.PRODUCT_NOT_FOUND) }
    }

    private fun validateOwner(product: Product, sellerId: Long) {
        if (!product.isOwnedBy(sellerId)) {
            throw CustomException(ErrorCode.FORBIDDEN)
        }
    }
}
