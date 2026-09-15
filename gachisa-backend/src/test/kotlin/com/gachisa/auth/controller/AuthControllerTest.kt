package com.gachisa.auth.controller

import com.gachisa.auth.service.AuthService
import com.gachisa.global.security.JwtTokenProvider
import com.gachisa.user.dto.UserInfo
import com.gachisa.user.entity.UserRole
import com.gachisa.user.entity.UserStatus
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

// signup/login은 permitAll 대상이라 시큐리티 필터와 무관하게 @Valid 검증만 확인하면 되므로
// (WebMvcTest 슬라이스는 실제 SecurityConfig를 로드하지 않아 기본 Basic Auth로 막히는 문제를 피한다)
@WebMvcTest(AuthController::class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var authService: AuthService

    @MockkBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"email": "", "password": "1234", "name": "구매자", "role": "ROLE_BUYER"}""",
            """{"email": "이메일아님", "password": "1234", "name": "구매자", "role": "ROLE_BUYER"}""",
            """{"email": "buyer@test.com", "password": "", "name": "구매자", "role": "ROLE_BUYER"}""",
            """{"email": "buyer@test.com", "password": "123", "name": "구매자", "role": "ROLE_BUYER"}""",
            """{"email": "buyer@test.com", "password": "1234", "name": "", "role": "ROLE_BUYER"}""",
        ],
    )
    fun signupWithInvalidFieldReturnsBadRequest(body: String) {
        mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isBadRequest)

        verify(exactly = 0) { authService.signUp(any(), any(), any(), any()) }
    }

    @Test
    fun signupWithValidBodyReachesService() {
        every { authService.signUp("buyer@test.com", "1234", "구매자", UserRole.ROLE_BUYER) } returns
            UserInfo(1L, "buyer@test.com", "구매자", UserRole.ROLE_BUYER, LocalDateTime.now(), UserStatus.ACTIVE)

        mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "buyer@test.com", "password": "1234", "name": "구매자", "role": "ROLE_BUYER"}"""),
        ).andExpect(status().isOk)

        verify { authService.signUp("buyer@test.com", "1234", "구매자", UserRole.ROLE_BUYER) }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"email": "", "password": "1234"}""",
            """{"email": "buyer@test.com", "password": ""}""",
        ],
    )
    fun loginWithInvalidFieldReturnsBadRequest(body: String) {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isBadRequest)

        verify(exactly = 0) { authService.login(any(), any()) }
    }
}
