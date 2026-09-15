package com.gachisa.user.controller

import com.gachisa.global.security.AuthenticatedUser
import com.gachisa.global.security.CustomUserDetails
import com.gachisa.global.security.JwtTokenProvider
import com.gachisa.user.dto.UserAdminResponse
import com.gachisa.user.entity.UserProvider
import com.gachisa.user.entity.UserRole
import com.gachisa.user.entity.UserStatus
import com.gachisa.user.service.AdminUserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(AdminUserController::class)
class AdminUserControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var adminUserService: AdminUserService

    @MockkBean
    lateinit var jwtTokenProvider: JwtTokenProvider

    private fun admin(): CustomUserDetails =
        CustomUserDetails(AuthenticatedUser(1L, "admin@test.com", UserRole.ROLE_ADMIN))

    private fun response(status: UserStatus): UserAdminResponse =
        UserAdminResponse(3L, "buyer1@test.com", "구매자1", UserRole.ROLE_BUYER, UserProvider.LOCAL, status, LocalDateTime.now())

    @Test
    fun suspendUserAsAdminReachesService() {
        every { adminUserService.suspendUser(3L) } returns response(UserStatus.SUSPENDED)

        mockMvc.perform(
            patch("/api/admin/users/3/suspend")
                .with(user(admin()))
                .with(csrf()),
        ).andExpect(status().isOk)

        verify { adminUserService.suspendUser(3L) }
    }

    @Test
    fun reinstateUserAsAdminReachesService() {
        every { adminUserService.reinstateUser(3L) } returns response(UserStatus.ACTIVE)

        mockMvc.perform(
            patch("/api/admin/users/3/reinstate")
                .with(user(admin()))
                .with(csrf()),
        ).andExpect(status().isOk)

        verify { adminUserService.reinstateUser(3L) }
    }

    @Test
    fun withdrawUserAsAdminReachesService() {
        every { adminUserService.withdrawUser(3L) } returns response(UserStatus.WITHDRAWN)

        mockMvc.perform(
            patch("/api/admin/users/3/withdraw")
                .with(user(admin()))
                .with(csrf()),
        ).andExpect(status().isOk)

        verify { adminUserService.withdrawUser(3L) }
    }
}
