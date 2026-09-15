package com.gachisa.user.controller

import com.gachisa.global.security.AuthenticatedUser
import com.gachisa.global.security.CustomUserDetails
import com.gachisa.global.security.JwtTokenProvider
import com.gachisa.user.dto.UserInfo
import com.gachisa.user.entity.UserRole
import com.gachisa.user.entity.UserStatus
import com.gachisa.user.service.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(UserController::class)
class UserControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var userService: UserService

    @MockkBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    private fun buyer(): CustomUserDetails =
        CustomUserDetails(AuthenticatedUser(1L, "buyer@test.com", UserRole.ROLE_BUYER))

    @Test
    fun updateMeWithTooShortNewPasswordReturnsBadRequest() {
        mockMvc.perform(
            patch("/api/users/me")
                .with(user(buyer()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "1234", "newPassword": "123"}"""),
        ).andExpect(status().isBadRequest)

        verify(exactly = 0) { userService.updateMe(any(), any(), any(), any()) }
    }

    @Test
    fun updateMeWithOnlyNameFieldIsAccepted() {
        every { userService.updateMe(1L, "새이름", null, null) } returns
            UserInfo(1L, "buyer@test.com", "새이름", UserRole.ROLE_BUYER, LocalDateTime.now(), UserStatus.ACTIVE)

        mockMvc.perform(
            patch("/api/users/me")
                .with(user(buyer()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "새이름"}"""),
        ).andExpect(status().isOk)

        verify { userService.updateMe(1L, "새이름", null, null) }
    }
}
