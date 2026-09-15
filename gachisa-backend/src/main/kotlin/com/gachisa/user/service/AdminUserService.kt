package com.gachisa.user.service

import com.gachisa.auth.service.RefreshTokenService
import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.user.dto.UserAdminResponse
import com.gachisa.user.entity.User
import com.gachisa.user.entity.UserRole
import com.gachisa.user.entity.UserStatus
import com.gachisa.user.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AdminUserService(
    private val userRepository: UserRepository,
    private val refreshTokenService: RefreshTokenService,
) {

    fun getUsers(email: String?, role: UserRole?, status: UserStatus?, pageable: Pageable): Page<UserAdminResponse> {
        return userRepository.search(email, role, status, pageable).map { UserAdminResponse.from(it) }
    }

    fun getUser(userId: Long): UserAdminResponse {
        return UserAdminResponse.from(findUser(userId))
    }

    @Transactional
    fun suspendUser(userId: Long): UserAdminResponse {
        val user = findManagedTarget(userId)
        user.suspend()
        // 즉시 로그인/재발급을 막기 위해 리프레시 토큰을 전량 폐기한다.
        // 이미 발급된 액세스 토큰은 만료 전까지 유효하다 (auth 도메인과 별도 논의 필요).
        refreshTokenService.revokeAllByUser(userId)
        return UserAdminResponse.from(user)
    }

    @Transactional
    fun reinstateUser(userId: Long): UserAdminResponse {
        val user = findManagedTarget(userId)
        user.reinstate()
        return UserAdminResponse.from(user)
    }

    @Transactional
    fun withdrawUser(userId: Long): UserAdminResponse {
        val user = findManagedTarget(userId)
        user.withdraw()
        refreshTokenService.revokeAllByUser(userId)
        return UserAdminResponse.from(user)
    }

    private fun findManagedTarget(userId: Long): User {
        val user = findUser(userId)
        if (user.role == UserRole.ROLE_ADMIN) {
            throw CustomException(ErrorCode.ADMIN_ACCOUNT_NOT_MANAGEABLE)
        }
        return user
    }

    private fun findUser(userId: Long): User {
        return userRepository.findById(userId)
            .orElseThrow { CustomException(ErrorCode.USER_NOT_FOUND) }
    }
}
