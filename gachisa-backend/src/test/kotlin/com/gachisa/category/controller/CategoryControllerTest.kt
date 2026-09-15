package com.gachisa.category.controller

import com.gachisa.category.dto.CategoryResponse
import com.gachisa.category.service.CategoryService
import com.gachisa.global.security.AuthenticatedUser
import com.gachisa.global.security.CustomUserDetails
import com.gachisa.global.security.JwtTokenProvider
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(CategoryController::class)
class CategoryControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var categoryService: CategoryService

    @MockkBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    private fun admin(): CustomUserDetails =
        CustomUserDetails(AuthenticatedUser(1L, "admin@test.com", UserRole.ROLE_ADMIN))

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun createCategoryWithBlankNameReturnsBadRequest(blankName: String) {
        mockMvc.perform(
            post("/api/categories")
                .with(user(admin()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "$blankName", "parentId": null}"""),
        ).andExpect(status().isBadRequest)

        verify(exactly = 0) { categoryService.createCategory(any(), any()) }
    }

    @Test
    fun createCategoryWithValidNameReachesService() {
        every { categoryService.createCategory("전자제품", null) } returns
            CategoryResponse(1L, "전자제품", null, emptyList())

        mockMvc.perform(
            post("/api/categories")
                .with(user(admin()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "전자제품", "parentId": null}"""),
        ).andExpect(status().isCreated)

        verify { categoryService.createCategory("전자제품", null) }
    }

    @Test
    fun updateCategoryWithBlankNameReturnsBadRequest() {
        mockMvc.perform(
            patch("/api/categories/1")
                .with(user(admin()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": ""}"""),
        ).andExpect(status().isBadRequest)

        verify(exactly = 0) { categoryService.updateCategory(any(), any()) }
    }
}
