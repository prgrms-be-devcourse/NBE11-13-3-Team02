package com.gachisa.product.controller

import com.gachisa.global.security.AuthenticatedUser
import com.gachisa.global.security.CustomUserDetails
import com.gachisa.global.security.JwtTokenProvider
import com.gachisa.global.storage.ImageStorageService
import com.gachisa.product.dto.ProductResponse
import com.gachisa.product.service.ProductService
import com.gachisa.user.entity.UserRole
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

// addFilters(false)는 쓰지 않는다: SecurityMockMvcRequestPostProcessors.user(...)가
// @AuthenticationPrincipal을 채우는 메커니즘 자체가 시큐리티 필터 체인에 얹혀서 동작하기 때문에,
// 필터를 꺼버리면 인증된 사용자로 요청을 보내는 테스트에서 userDetails가 null이 되어버린다
// (실제로 이 문제로 create/update 테스트가 NPE로 500이 났던 적이 있음).
// search 테스트도 인증 여부와 무관한 @PositiveOrZero 검증만 확인하면 되므로 그냥 .with(user(...))를 붙여서 통일한다.
@WebMvcTest(ProductController::class)
class ProductControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var productService: ProductService

    @MockkBean
    lateinit var imageStorageService: ImageStorageService

    @MockkBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    private fun seller(): CustomUserDetails =
        CustomUserDetails(AuthenticatedUser(3L, "seller@test.com", UserRole.ROLE_SELLER))

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"name": "", "description": "설명", "basePrice": 1000, "stock": 10, "categoryId": 1}""",
            """{"name": "텀블러", "description": "설명", "basePrice": 0, "stock": 10, "categoryId": 1}""",
            """{"name": "텀블러", "description": "설명", "basePrice": -1000, "stock": 10, "categoryId": 1}""",
            """{"name": "텀블러", "description": "설명", "basePrice": 1000, "stock": -1, "categoryId": 1}""",
            """{"name": "텀블러", "description": "설명", "basePrice": 1000, "stock": 10, "categoryId": null}""",
        ],
    )
    fun createProductWithInvalidFieldReturnsBadRequest(dataJson: String) {
        val data = MockMultipartFile("data", "data", MediaType.APPLICATION_JSON_VALUE, dataJson.toByteArray())

        mockMvc.perform(
            multipart("/api/products")
                .file(data)
                .with(user(seller()))
                .with(csrf()),
        ).andExpect(status().isBadRequest)

        verify(exactly = 0) { productService.createProduct(any(), any(), any()) }
    }

    @Test
    fun createProductWithValidFieldReachesService() {
        val data = MockMultipartFile(
            "data", "data", MediaType.APPLICATION_JSON_VALUE,
            """{"name": "텀블러", "description": "설명", "basePrice": 1000, "stock": 10, "categoryId": 1}""".toByteArray(),
        )
        every { productService.createProduct(3L, any(), null) } returns
            ProductResponse(
                1L, 3L, "판매자", 1L, "카테고리", "텀블러", "설명",
                1000, 10, null, "ON_SALE", LocalDateTime.now(),
            )

        mockMvc.perform(
            multipart("/api/products")
                .file(data)
                .with(user(seller()))
                .with(csrf()),
        ).andExpect(status().isCreated)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"basePrice": 0}""",
            """{"stock": -5}""",
        ],
    )
    fun updateProductWithInvalidFieldReturnsBadRequest(body: String) {
        mockMvc.perform(
            patch("/api/products/1")
                .with(user(seller()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isBadRequest)

        verify(exactly = 0) { productService.updateProduct(any(), any(), any()) }
    }

    @Test
    fun updateProductWithPartialNullFieldsIsAccepted() {
        every { productService.updateProduct(1L, 3L, any()) } returns
            ProductResponse(
                1L, 3L, "판매자", 1L, "카테고리", "텀블러", "설명",
                18000, 10, null, "ON_SALE", LocalDateTime.now(),
            )

        mockMvc.perform(
            patch("/api/products/1")
                .with(user(seller()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"basePrice": 18000}"""),
        ).andExpect(status().isOk)
    }

    @ParameterizedTest
    @ValueSource(strings = ["minPrice", "maxPrice"])
    fun searchProductsWithNegativePriceReturnsBadRequest(paramName: String) {
        mockMvc.perform(get("/api/products/search").with(user(seller())).param(paramName, "-1"))
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { productService.searchProducts(any(), any(), any(), any()) }
    }

    @Test
    fun searchProductsWithValidPriceRangeReachesService() {
        every { productService.searchProducts(null, 1000, 20000, null) } returns emptyList()

        mockMvc.perform(
            get("/api/products/search").with(user(seller()))
                .param("minPrice", "1000").param("maxPrice", "20000"),
        ).andExpect(status().isOk)

        verify { productService.searchProducts(null, 1000, 20000, null) }
    }
}
