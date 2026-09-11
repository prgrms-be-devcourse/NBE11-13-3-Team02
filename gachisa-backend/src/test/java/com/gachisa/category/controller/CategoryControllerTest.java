package com.gachisa.category.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachisa.category.dto.CategoryResponse;
import com.gachisa.category.service.CategoryService;
import com.gachisa.global.security.AuthenticatedUser;
import com.gachisa.global.security.CustomUserDetails;
import com.gachisa.global.security.JwtTokenProvider;
import com.gachisa.user.entity.UserRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CategoryService categoryService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    private CustomUserDetails admin() {
        return new CustomUserDetails(new AuthenticatedUser(1L, "admin@test.com", UserRole.ROLE_ADMIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void createCategoryWithBlankNameReturnsBadRequest(String blankName) throws Exception {
        mockMvc.perform(post("/api/categories")
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"parentId\": null}".formatted(blankName)))
                .andExpect(status().isBadRequest());

        verify(categoryService, never()).createCategory(any(), any());
    }

    @Test
    void createCategoryWithValidNameReachesService() throws Exception {
        given(categoryService.createCategory(eq("전자제품"), any()))
                .willReturn(new CategoryResponse(1L, "전자제품", null, List.of()));

        mockMvc.perform(post("/api/categories")
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "전자제품", "parentId": null}
                                """))
                .andExpect(status().isCreated());

        verify(categoryService).createCategory("전자제품", null);
    }

    @Test
    void updateCategoryWithBlankNameReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/categories/1")
                        .with(user(admin()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest());

        verify(categoryService, never()).updateCategory(any(), any());
    }
}
