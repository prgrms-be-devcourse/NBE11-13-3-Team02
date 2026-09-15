package com.gachisa.user.service

import com.gachisa.auth.service.RefreshTokenService
import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.user.entity.User
import com.gachisa.user.entity.UserRole
import com.gachisa.user.entity.UserStatus
import com.gachisa.user.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.Optional

class AdminUserServiceTest {

    private val userRepository: UserRepository = mockk()
    private val refreshTokenService: RefreshTokenService = mockk(relaxed = true)

    private lateinit var adminUserService: AdminUserService

    @BeforeEach
    fun setUp() {
        adminUserService = AdminUserService(userRepository, refreshTokenService)
    }

    @Test
    fun getUsersReturnsMappedPage() {
        val user = user("buyer1@test.com", UserRole.ROLE_BUYER, UserStatus.ACTIVE)
        val pageable = PageRequest.of(0, 20)
        every { userRepository.search(null, null, null, pageable) } returns PageImpl(listOf(user))

        val result = adminUserService.getUsers(null, null, null, pageable)

        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].email).isEqualTo("buyer1@test.com")
    }

    @Test
    fun getUserThrowsWhenNotFound() {
        every { userRepository.findById(USER_ID) } returns Optional.empty()

        assertThatThrownBy { adminUserService.getUser(USER_ID) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.USER_NOT_FOUND)
    }

    @Test
    fun suspendUserSuspendsAndRevokesRefreshTokens() {
        val user = user("buyer1@test.com", UserRole.ROLE_BUYER, UserStatus.ACTIVE)
        every { userRepository.findById(USER_ID) } returns Optional.of(user)

        val response = adminUserService.suspendUser(USER_ID)

        assertThat(response.status).isEqualTo(UserStatus.SUSPENDED)
        verify { refreshTokenService.revokeAllByUser(USER_ID) }
    }

    @Test
    fun suspendUserThrowsWhenTargetIsAdmin() {
        val admin = user("admin@test.com", UserRole.ROLE_ADMIN, UserStatus.ACTIVE)
        every { userRepository.findById(USER_ID) } returns Optional.of(admin)

        assertThatThrownBy { adminUserService.suspendUser(USER_ID) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.ADMIN_ACCOUNT_NOT_MANAGEABLE)
        verify(exactly = 0) { refreshTokenService.revokeAllByUser(any()) }
    }

    @Test
    fun suspendUserThrowsWhenAlreadySuspended() {
        val user = user("buyer1@test.com", UserRole.ROLE_BUYER, UserStatus.SUSPENDED)
        every { userRepository.findById(USER_ID) } returns Optional.of(user)

        assertThatThrownBy { adminUserService.suspendUser(USER_ID) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.ACCOUNT_ALREADY_SUSPENDED)
    }

    @Test
    fun reinstateUserReturnsAccountToActive() {
        val user = user("buyer1@test.com", UserRole.ROLE_BUYER, UserStatus.SUSPENDED)
        every { userRepository.findById(USER_ID) } returns Optional.of(user)

        val response = adminUserService.reinstateUser(USER_ID)

        assertThat(response.status).isEqualTo(UserStatus.ACTIVE)
    }

    @Test
    fun reinstateUserThrowsWhenNotSuspended() {
        val user = user("buyer1@test.com", UserRole.ROLE_BUYER, UserStatus.ACTIVE)
        every { userRepository.findById(USER_ID) } returns Optional.of(user)

        assertThatThrownBy { adminUserService.reinstateUser(USER_ID) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.ACCOUNT_NOT_SUSPENDED)
    }

    @Test
    fun withdrawUserWithdrawsAndRevokesRefreshTokens() {
        val user = user("buyer1@test.com", UserRole.ROLE_BUYER, UserStatus.ACTIVE)
        every { userRepository.findById(USER_ID) } returns Optional.of(user)

        val response = adminUserService.withdrawUser(USER_ID)

        assertThat(response.status).isEqualTo(UserStatus.WITHDRAWN)
        verify { refreshTokenService.revokeAllByUser(USER_ID) }
    }

    @Test
    fun withdrawUserThrowsWhenAlreadyWithdrawn() {
        val user = user("buyer1@test.com", UserRole.ROLE_BUYER, UserStatus.WITHDRAWN)
        every { userRepository.findById(USER_ID) } returns Optional.of(user)

        assertThatThrownBy { adminUserService.withdrawUser(USER_ID) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.ACCOUNT_ALREADY_WITHDRAWN)
    }

    private fun user(email: String, role: UserRole, status: UserStatus): User {
        val user = User.of(
            email = email,
            password = "encoded-password",
            name = "테스트유저",
            role = role,
            provider = null,
            providerId = null,
            createdAt = NOW,
        )
        ReflectionTestUtils.setField(user, "id", USER_ID)
        ReflectionTestUtils.setField(user, "status", status)
        return user
    }

    companion object {
        private const val USER_ID = 1L
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 18, 12, 0)
    }
}
