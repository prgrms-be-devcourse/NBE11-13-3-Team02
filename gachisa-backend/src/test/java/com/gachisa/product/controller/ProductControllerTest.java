package com.gachisa.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachisa.global.security.AuthenticatedUser;
import com.gachisa.global.security.CustomUserDetails;
import com.gachisa.global.security.JwtTokenProvider;
import com.gachisa.global.storage.ImageStorageService;
import com.gachisa.product.dto.ProductResponse;
import com.gachisa.product.service.ProductService;
import com.gachisa.user.entity.UserRole;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// addFilters(false)는 쓰지 않는다: SecurityMockMvcRequestPostProcessors.user(...)가
// @AuthenticationPrincipal을 채우는 메커니즘 자체가 시큐리티 필터 체인에 얹혀서 동작하기 때문에,
// 필터를 꺼버리면 인증된 사용자로 요청을 보내는 테스트에서 userDetails가 null이 되어버린다
// (실제로 이 문제로 create/update 테스트가 NPE로 500이 났던 적이 있음).
// search 테스트도 인증 여부와 무관한 @PositiveOrZero 검증만 확인하면 되므로 그냥 .with(user(...))를 붙여서 통일한다.
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ProductService productService;
    @MockitoBean ImageStorageService imageStorageService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    private CustomUserDetails seller() {
        return new CustomUserDetails(new AuthenticatedUser(3L, "seller@test.com", UserRole.ROLE_SELLER));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"name": "", "description": "설명", "basePrice": 1000, "stock": 10, "categoryId": 1}
            """,
            """
            {"name": "텀블러", "description": "설명", "basePrice": 0, "stock": 10, "categoryId": 1}
            """,
            """
            {"name": "텀블러", "description": "설명", "basePrice": -1000, "stock": 10, "categoryId": 1}
            """,
            """
            {"name": "텀블러", "description": "설명", "basePrice": 1000, "stock": -1, "categoryId": 1}
            """,
            """
            {"name": "텀블러", "description": "설명", "basePrice": 1000, "stock": 10, "categoryId": null}
            """
    })
    void createProductWithInvalidFieldReturnsBadRequest(String dataJson) throws Exception {
        MockMultipartFile data = new MockMultipartFile(
                "data", "data", MediaType.APPLICATION_JSON_VALUE, dataJson.getBytes());

        mockMvc.perform(multipart("/api/products")
                        .file(data)
                        .with(user(seller()))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(productService, never()).createProduct(any(), any(), any());
    }

    @Test
    void createProductWithValidFieldReachesService() throws Exception {
        MockMultipartFile data = new MockMultipartFile(
                "data", "data", MediaType.APPLICATION_JSON_VALUE,
                """
                {"name": "텀블러", "description": "설명", "basePrice": 1000, "stock": 10, "categoryId": 1}
                """.getBytes());
        given(productService.createProduct(eq(3L), any(), eq(null)))
                .willReturn(new ProductResponse(1L, 3L, "판매자", 1L, "카테고리", "텀블러", "설명",
                        1000, 10, null, "ON_SALE", LocalDateTime.now()));

        mockMvc.perform(multipart("/api/products")
                        .file(data)
                        .with(user(seller()))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"basePrice": 0}
            """,
            """
            {"stock": -5}
            """
    })
    void updateProductWithInvalidFieldReturnsBadRequest(String body) throws Exception {
        mockMvc.perform(patch("/api/products/1")
                        .with(user(seller()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(productService, never()).updateProduct(any(), any(), any());
    }

    @Test
    void updateProductWithPartialNullFieldsIsAccepted() throws Exception {
        given(productService.updateProduct(eq(1L), eq(3L), any()))
                .willReturn(new ProductResponse(1L, 3L, "판매자", 1L, "카테고리", "텀블러", "설명",
                        18000, 10, null, "ON_SALE", LocalDateTime.now()));

        mockMvc.perform(patch("/api/products/1")
                        .with(user(seller()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"basePrice": 18000}
                                """))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"minPrice", "maxPrice"})
    void searchProductsWithNegativePriceReturnsBadRequest(String paramName) throws Exception {
        mockMvc.perform(get("/api/products/search").with(user(seller())).param(paramName, "-1"))
                .andExpect(status().isBadRequest());

        verify(productService, never()).searchProducts(any(), any(), any(), any());
    }

    @Test
    void searchProductsWithValidPriceRangeReachesService() throws Exception {
        mockMvc.perform(get("/api/products/search").with(user(seller()))
                        .param("minPrice", "1000").param("maxPrice", "20000"))
                .andExpect(status().isOk());

        verify(productService).searchProducts(null, 1000, 20000, null);
    }
}
