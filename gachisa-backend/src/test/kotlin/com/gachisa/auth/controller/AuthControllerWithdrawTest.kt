package com.gachisa.auth.controller

import com.gachisa.auth.service.AuthService
import com.gachisa.global.security.AuthenticatedUser
import com.gachisa.global.security.CustomUserDetails
import com.gachisa.global.security.JwtTokenProvider
import com.gachisa.user.entity.UserRole
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// /api/auth/withdraw는 인증이 필요한 엔드포인트라 AuthControllerTest와 달리
// 필터를 비활성화하지 않는다 (addFilters=false면 @AuthenticationPrincipal이 null로 resolve됨).
@WebMvcTest(AuthController::class)
class AuthControllerWithdrawTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var authService: AuthService

    @MockkBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    private fun buyer(): CustomUserDetails =
        CustomUserDetails(AuthenticatedUser(1L, "buyer@test.com", UserRole.ROLE_BUYER))

    @Test
    fun withdrawWithAuthenticatedUserReachesService() {
        every { authService.withdraw(1L, "1234") } just Runs

        mockMvc.perform(
            post("/api/auth/withdraw")
                .with(user(buyer()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password": "1234"}"""),
        ).andExpect(status().isOk)

        verify { authService.withdraw(1L, "1234") }
    }

    @Test
    fun withdrawWithoutBodyPassesNullPassword() {
        every { authService.withdraw(1L, null) } just Runs

        mockMvc.perform(
            post("/api/auth/withdraw")
                .with(user(buyer()))
                .with(csrf()),
        ).andExpect(status().isOk)

        verify { authService.withdraw(1L, null) }
    }
}
