package com.gachisa.user.service

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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.Optional

class UserServiceTest {

    private val userRepository: UserRepository = mockk()
    private val passwordEncoder: PasswordEncoder = mockk()

    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userService = UserService(userRepository, passwordEncoder)
    }

    @Test
    fun getByIdReturnsUserInfo() {
        val user = user("buyer1@test.com", "encoded-password", "구매자1", UserRole.ROLE_BUYER)
        every { userRepository.findById(USER_ID) } returns Optional.of(user)

        val userInfo = userService.getById(USER_ID)

        assertThat(userInfo.id).isEqualTo(USER_ID)
        assertThat(userInfo.email).isEqualTo("buyer1@test.com")
        assertThat(userInfo.name).isEqualTo("구매자1")
        assertThat(userInfo.role).isEqualTo(UserRole.ROLE_BUYER)
    }

    @Test
    fun getByIdThrowsWhenUserNotFound() {
        every { userRepository.findById(USER_ID) } returns Optional.empty()

        assertThatThrownBy { userService.getById(USER_ID) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.USER_NOT_FOUND)
    }

    @Test
    fun updateMeChangesNameOnly() {
        val user = user("buyer1@test.com", "encoded-password", "구매자1", UserRole.ROLE_BUYER)
        every { userRepository.findById(USER_ID) } returns Optional.of(user)

        val userInfo = userService.updateMe(USER_ID, "새이름", null, null)

        assertThat(userInfo.name).isEqualTo("새이름")
        assertThat(user.password).isEqualTo("encoded-password")
        verify(exactly = 0) { passwordEncoder.encode(any()) }
    }

    @Test
    fun updateMeChangesPasswordWhenCurrentPasswordMatches() {
        val user = user("buyer1@test.com", "encoded-old-password", "구매자1", UserRole.ROLE_BUYER)
        every { userRepository.findById(USER_ID) } returns Optional.of(user)
        every { passwordEncoder.matches("old-password", "encoded-old-password") } returns true
        every { passwordEncoder.encode("new-password") } returns "encoded-new-password"

        userService.updateMe(USER_ID, null, "old-password", "new-password")

        assertThat(user.password).isEqualTo("encoded-new-password")
    }

    @Test
    fun updateMeThrowsWhenCurrentPasswordDoesNotMatch() {
        val user = user("buyer1@test.com", "encoded-old-password", "구매자1", UserRole.ROLE_BUYER)
        every { userRepository.findById(USER_ID) } returns Optional.of(user)
        every { passwordEncoder.matches("wrong-password", "encoded-old-password") } returns false

        assertThatThrownBy { userService.updateMe(USER_ID, null, "wrong-password", "new-password") }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.INVALID_CREDENTIALS)
        verify(exactly = 0) { passwordEncoder.encode(any()) }
    }

    @Test
    fun authenticateReturnsUserInfoWhenActive() {
        val user = user("buyer1@test.com", "encoded-password", "구매자1", UserRole.ROLE_BUYER)
        every { userRepository.findByEmail("buyer1@test.com") } returns Optional.of(user)
        every { passwordEncoder.matches("1234", "encoded-password") } returns true

        val userInfo = userService.authenticate("buyer1@test.com", "1234")

        assertThat(userInfo.status).isEqualTo(UserStatus.ACTIVE)
    }

    @Test
    fun authenticateThrowsWhenAccountSuspended() {
        val user = user("buyer1@test.com", "encoded-password", "구매자1", UserRole.ROLE_BUYER)
        user.suspend()
        every { userRepository.findByEmail("buyer1@test.com") } returns Optional.of(user)
        every { passwordEncoder.matches("1234", "encoded-password") } returns true

        assertThatThrownBy { userService.authenticate("buyer1@test.com", "1234") }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED)
    }

    @Test
    fun authenticateThrowsWhenAccountWithdrawn() {
        val user = user("buyer1@test.com", "encoded-password", "구매자1", UserRole.ROLE_BUYER)
        user.withdraw()
        every { userRepository.findByEmail("buyer1@test.com") } returns Optional.of(user)
        every { passwordEncoder.matches("1234", "encoded-password") } returns true

        assertThatThrownBy { userService.authenticate("buyer1@test.com", "1234") }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN)
    }

    private fun user(email: String, password: String, name: String, role: UserRole): User {
        val user = User.of(
            email = email,
            password = password,
            name = name,
            role = role,
            provider = null,
            providerId = null,
            createdAt = NOW,
        )
        ReflectionTestUtils.setField(user, "id", USER_ID)
        return user
    }

    companion object {
        private const val USER_ID = 1L
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 18, 12, 0)
    }
}
