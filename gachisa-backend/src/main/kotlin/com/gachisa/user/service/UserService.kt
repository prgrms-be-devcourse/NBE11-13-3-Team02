package com.gachisa.user.service

import com.gachisa.auth.client.OAuthUserInfo
import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.util.TimeProvider
import com.gachisa.user.dto.UserInfo
import com.gachisa.user.entity.User
import com.gachisa.user.entity.UserProvider
import com.gachisa.user.entity.UserRole
import com.gachisa.user.entity.UserStatus
import com.gachisa.user.entity.WithdrawnEmail
import com.gachisa.user.repository.UserRepository
import com.gachisa.user.repository.WithdrawnEmailRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
    private val withdrawnEmailRepository: WithdrawnEmailRepository,
    private val passwordEncoder: PasswordEncoder,
    private val timeProvider: TimeProvider,
) {

    @Transactional
    fun signUp(email: String, rawPassword: String, name: String, role: UserRole): UserInfo {
        if (userRepository.existsByEmail(email)) {
            throw CustomException(ErrorCode.EMAIL_DUPLICATED)
        }
        assertEmailNotRecentlyWithdrawn(email)

        val user = User.of(
            email = email,
            password = passwordEncoder.encode(rawPassword),
            name = name,
            role = role,
            provider = UserProvider.LOCAL,
            providerId = null,
            createdAt = LocalDateTime.now(),
        )

        val saved = userRepository.save(user)
        return toUserInfo(saved)
    }

    fun authenticate(email: String, rawPassword: String): UserInfo {
        val user = userRepository.findByEmail(email)
            .orElseThrow { CustomException(ErrorCode.INVALID_CREDENTIALS) }

        if (user.password == null || !passwordEncoder.matches(rawPassword, user.password)) {
            throw CustomException(ErrorCode.INVALID_CREDENTIALS)
        }

        validateActive(user)
        return toUserInfo(user)
    }

    fun getById(userId: Long): UserInfo {
        val user = userRepository.findById(userId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
        return toUserInfo(user)
    }

    @Transactional
    fun updateMe(userId: Long, name: String?, currentPassword: String?, newPassword: String?): UserInfo {
        val user = userRepository.findById(userId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        if (!name.isNullOrBlank()) {
            user.updateName(name)
        }

        if (newPassword != null && newPassword.isNotBlank()) {
            // 소셜 전용 계정(비밀번호 없음)은 현재 비밀번호 검증 없이 최초 비밀번호를 설정할 수 있게 한다.
            val storedPassword = user.password
            if (storedPassword != null && !passwordEncoder.matches(currentPassword ?: "", storedPassword)) {
                throw CustomException(ErrorCode.INVALID_CREDENTIALS)
            }
            user.changePassword(passwordEncoder.encode(newPassword)!!)
        }

        return toUserInfo(user)
    }

    @Transactional
    fun findOrCreateOAuthUser(provider: UserProvider, oAuthUserInfo: OAuthUserInfo): UserInfo {
        val linked = userRepository.findByProviderAndProviderId(provider, oAuthUserInfo.providerId)
        if (linked.isPresent) {
            validateActive(linked.get())
            return toUserInfo(linked.get())
        }

        val email = oAuthUserInfo.email

        // 제공자가 이메일 인증을 확인해준 경우에만 기존 일반가입 계정에 자동으로 연동한다.
        if (oAuthUserInfo.emailVerified) {
            val existing = userRepository.findByEmail(email)
            if (existing.isPresent) {
                validateActive(existing.get())
                existing.get().linkOAuthAccount(provider, oAuthUserInfo.providerId)
                return toUserInfo(existing.get())
            }
        } else if (userRepository.existsByEmail(email)) {
            // 인증되지 않은 이메일이 이미 다른 계정에서 쓰이고 있다면, 그 계정을 가로채지 못하게 막는다.
            throw CustomException(ErrorCode.EMAIL_DUPLICATED)
        }
        assertEmailNotRecentlyWithdrawn(email)

        val user = User.of(
            email = email,
            password = null,
            name = oAuthUserInfo.name,
            role = UserRole.ROLE_BUYER,
            provider = provider,
            providerId = oAuthUserInfo.providerId,
            createdAt = LocalDateTime.now(),
        )

        return toUserInfo(userRepository.save(user))
    }

    @Transactional
    fun withdraw(userId: Long, rawPassword: String?): UserInfo {
        val user = userRepository.findById(userId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }

        val storedPassword = user.password
        if (storedPassword != null && !passwordEncoder.matches(rawPassword ?: "", storedPassword)) {
            throw CustomException(ErrorCode.INVALID_CREDENTIALS)
        }

        val originalEmail = user.email
        user.withdraw()
        if (originalEmail != null) {
            recordWithdrawnEmail(originalEmail)
        }

        return toUserInfo(user)
    }

    private fun assertEmailNotRecentlyWithdrawn(email: String) {
        val withdrawnEmail = withdrawnEmailRepository.findByEmail(email).orElse(null) ?: return
        if (timeProvider.now().isBefore(withdrawnEmail.withdrawnAt.plus(WITHDRAWAL_COOLDOWN))) {
            throw CustomException(ErrorCode.RECENTLY_WITHDRAWN_EMAIL)
        }
    }

    private fun recordWithdrawnEmail(email: String) {
        val now = timeProvider.now()
        val existing = withdrawnEmailRepository.findByEmail(email).orElse(null)
        if (existing != null) {
            existing.renewWithdrawnAt(now)
        } else {
            withdrawnEmailRepository.save(WithdrawnEmail.of(email, now))
        }
    }

    private fun validateActive(user: User) {
        when (user.status) {
            UserStatus.SUSPENDED -> throw CustomException(ErrorCode.ACCOUNT_SUSPENDED)
            UserStatus.WITHDRAWN -> throw CustomException(ErrorCode.ACCOUNT_WITHDRAWN)
            UserStatus.ACTIVE -> {}
        }
    }

    private fun toUserInfo(user: User): UserInfo {
        return UserInfo(user.id!!, user.email, user.name, user.role, user.createdAt, user.status)
    }

    companion object {
        private val WITHDRAWAL_COOLDOWN: Duration = Duration.ofDays(1)
    }
}
