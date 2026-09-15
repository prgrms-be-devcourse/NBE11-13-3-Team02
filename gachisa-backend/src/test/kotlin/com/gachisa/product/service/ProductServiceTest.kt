package com.gachisa.product.service

import com.gachisa.category.entity.Category
import com.gachisa.category.repository.CategoryRepository
import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.product.dto.ProductCreateRequest
import com.gachisa.product.dto.ProductUpdateRequest
import com.gachisa.product.entity.Product
import com.gachisa.product.entity.ProductStatus
import com.gachisa.product.repository.ProductRepository
import com.gachisa.user.entity.User
import com.gachisa.user.entity.UserRole
import com.gachisa.user.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.Optional

class ProductServiceTest {

    private val productRepository: ProductRepository = mockk()
    private val categoryRepository: CategoryRepository = mockk()
    private val userRepository: UserRepository = mockk()

    private lateinit var productService: ProductService

    @BeforeEach
    fun setUp() {
        productService = ProductService(productRepository, categoryRepository, userRepository)
    }

    @Test
    fun createProductSetsStockFromRequest() {
        val category = category(CATEGORY_ID, "생활/리빙")
        val seller = seller(SELLER_ID, "판매자1")
        every { categoryRepository.findById(CATEGORY_ID) } returns Optional.of(category)
        every { userRepository.getReferenceById(SELLER_ID) } returns seller
        val request = ProductCreateRequest("텀블러", "보온 텀블러", 15000, 30, CATEGORY_ID)
        every { productRepository.existsBySellerIdAndName(SELLER_ID, "텀블러") } returns false
        every { productRepository.save(any()) } answers { firstArg() }

        val response = productService.createProduct(SELLER_ID, request, "http://img/1.png")

        assertThat(response.stock).isEqualTo(30)
        assertThat(response.imageUrl).isEqualTo("http://img/1.png")
    }

    @Test
    fun createProductThrowsWhenSameSellerHasDuplicateName() {
        val category = category(CATEGORY_ID, "생활/리빙")
        every { categoryRepository.findById(CATEGORY_ID) } returns Optional.of(category)
        every { productRepository.existsBySellerIdAndName(SELLER_ID, "텀블러") } returns true
        val request = ProductCreateRequest("텀블러", "보온 텀블러", 15000, 30, CATEGORY_ID)

        assertThatThrownBy { productService.createProduct(SELLER_ID, request, "http://img/1.png") }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.PRODUCT_NAME_DUPLICATED)
    }

    @Test
    fun createProductThrowsWhenCategoryNotFound() {
        every { categoryRepository.findById(CATEGORY_ID) } returns Optional.empty()
        val request = ProductCreateRequest("텀블러", "desc", 1000, 10, CATEGORY_ID)

        assertThatThrownBy { productService.createProduct(SELLER_ID, request, null) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND)
    }

    @Test
    fun getProductReturnsProductWithStock() {
        val product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 25)
        every { productRepository.findById(PRODUCT_ID) } returns Optional.of(product)

        val response = productService.getProduct(PRODUCT_ID)

        assertThat(response.id).isEqualTo(PRODUCT_ID)
        assertThat(response.stock).isEqualTo(25)
    }

    @Test
    fun getProductThrowsWhenNotFound() {
        every { productRepository.findById(PRODUCT_ID) } returns Optional.empty()

        assertThatThrownBy { productService.getProduct(PRODUCT_ID) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND)
    }

    @Test
    fun searchProductsDelegatesFiltersToRepository() {
        val product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10)
        every { productRepository.search(null, CATEGORY_ID, 1000, 20000, "원두") } returns listOf(product)

        val responses = productService.searchProducts(CATEGORY_ID, 1000, 20000, "원두")

        assertThat(responses).hasSize(1)
        verify { productRepository.search(null, CATEGORY_ID, 1000, 20000, "원두") }
    }

    @Test
    fun updateProductUpdatesOnlyProvidedFields() {
        val product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10)
        every { productRepository.findById(PRODUCT_ID) } returns Optional.of(product)
        val request = ProductUpdateRequest(null, null, 18000, null, null)

        val response = productService.updateProduct(PRODUCT_ID, SELLER_ID, request)

        assertThat(response.basePrice).isEqualTo(18000)
        assertThat(response.name).isEqualTo("텀블러")
        assertThat(response.stock).isEqualTo(10)
    }

    @Test
    fun updateProductUpdatesStock() {
        val product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10)
        every { productRepository.findById(PRODUCT_ID) } returns Optional.of(product)
        val request = ProductUpdateRequest(null, null, null, 40, null)

        val response = productService.updateProduct(PRODUCT_ID, SELLER_ID, request)

        assertThat(response.stock).isEqualTo(40)
    }

    @Test
    fun updateProductThrowsWhenNewNameDuplicatedForSameSeller() {
        val product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10)
        every { productRepository.findById(PRODUCT_ID) } returns Optional.of(product)
        every { productRepository.existsBySellerIdAndNameAndIdNot(SELLER_ID, "머그컵", PRODUCT_ID) } returns true
        val request = ProductUpdateRequest("머그컵", null, null, null, null)

        assertThatThrownBy { productService.updateProduct(PRODUCT_ID, SELLER_ID, request) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.PRODUCT_NAME_DUPLICATED)
    }

    @Test
    fun updateProductAllowsKeepingSameName() {
        val product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10)
        every { productRepository.findById(PRODUCT_ID) } returns Optional.of(product)
        val request = ProductUpdateRequest("텀블러", null, null, null, null)

        val response = productService.updateProduct(PRODUCT_ID, SELLER_ID, request)

        assertThat(response.name).isEqualTo("텀블러")
    }

    @Test
    fun updateProductThrowsWhenNotOwner() {
        val product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10)
        every { productRepository.findById(PRODUCT_ID) } returns Optional.of(product)
        val request = ProductUpdateRequest("해킹시도", null, null, null, null)

        assertThatThrownBy { productService.updateProduct(PRODUCT_ID, OTHER_SELLER_ID, request) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.FORBIDDEN)
    }

    @Test
    fun updateProductThrowsWhenNewCategoryNotFound() {
        val product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10)
        every { productRepository.findById(PRODUCT_ID) } returns Optional.of(product)
        every { categoryRepository.findById(999L) } returns Optional.empty()
        val request = ProductUpdateRequest(null, null, null, null, 999L)

        assertThatThrownBy { productService.updateProduct(PRODUCT_ID, SELLER_ID, request) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND)
    }

    @Test
    fun deleteProductStopsSaleWhenOwner() {
        val product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10)
        every { productRepository.findById(PRODUCT_ID) } returns Optional.of(product)

        productService.deleteProduct(PRODUCT_ID, SELLER_ID)

        assertThat(product.status).isEqualTo(ProductStatus.SUSPENDED)
    }

    @Test
    fun deleteProductThrowsWhenNotOwner() {
        val product = product(PRODUCT_ID, SELLER_ID, ProductStatus.ON_SALE, 10)
        every { productRepository.findById(PRODUCT_ID) } returns Optional.of(product)

        assertThatThrownBy { productService.deleteProduct(PRODUCT_ID, OTHER_SELLER_ID) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.FORBIDDEN)
        assertThat(product.status).isEqualTo(ProductStatus.ON_SALE)
    }

    @Test
    fun resumeProductResumesSaleWhenOwner() {
        val product = product(PRODUCT_ID, SELLER_ID, ProductStatus.SUSPENDED, 10)
        every { productRepository.findById(PRODUCT_ID) } returns Optional.of(product)

        val response = productService.resumeProduct(PRODUCT_ID, SELLER_ID)

        assertThat(response.status).isEqualTo(ProductStatus.ON_SALE.name)
    }

    private fun product(id: Long, sellerId: Long, status: ProductStatus, stock: Int): Product {
        val product = Product.of(
            seller = seller(sellerId, "판매자1"),
            category = category(CATEGORY_ID, "생활/리빙"),
            name = "텀블러",
            description = "보온 텀블러",
            basePrice = 15000,
            stock = stock,
            imageUrl = "http://img/1.png",
            status = status,
            createdAt = NOW,
        )
        ReflectionTestUtils.setField(product, "id", id)
        return product
    }

    private fun category(id: Long, name: String): Category {
        val category = Category.of(name, null)
        ReflectionTestUtils.setField(category, "id", id)
        return category
    }

    private fun seller(id: Long, name: String): User {
        val user = User.of("$id@test.com", "encoded", name, UserRole.ROLE_SELLER, null, null, NOW)
        ReflectionTestUtils.setField(user, "id", id)
        return user
    }

    companion object {
        private const val PRODUCT_ID = 1L
        private const val CATEGORY_ID = 10L
        private const val SELLER_ID = 100L
        private const val OTHER_SELLER_ID = 200L
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 18, 12, 0)
    }
}
