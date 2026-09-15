package com.gachisa.auth.service

import com.gachisa.auth.entity.RefreshToken
import com.gachisa.auth.repository.RefreshTokenCacheRepository
import com.gachisa.auth.repository.RefreshTokenRepository
import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.global.security.JwtProperties
import com.gachisa.global.security.TokenHashUtil
import com.gachisa.user.dto.UserInfo
import com.gachisa.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val refreshTokenCacheRepository: RefreshTokenCacheRepository,
    private val userService: UserService,
    private val tokenHashUtil: TokenHashUtil,
    private val jwtProperties: JwtProperties,
) {

    @Transactional
    fun issue(user: UserInfo): String {
        val rawRefreshToken = UUID.randomUUID().toString()
        val tokenHash = tokenHashUtil.sha256(rawRefreshToken)

        val now = LocalDateTime.now()
        val expiresAt = now.plus(jwtProperties.refreshTokenValidity())

        val refreshToken = RefreshToken.of(user.id, tokenHash, now, expiresAt)
        refreshTokenRepository.save(refreshToken)
        refreshTokenCacheRepository.save(tokenHash, user.id, jwtProperties.refreshTokenValidity())

        return rawRefreshToken
    }

    @Transactional(noRollbackFor = [CustomException::class])
    fun rotate(rawRefreshToken: String): UserInfo {
        val tokenHash = tokenHashUtil.sha256(rawRefreshToken)

        val cachedUserId = refreshTokenCacheRepository.findUserId(tokenHash)
        if (cachedUserId != null) {
            val updatedRows = refreshTokenRepository.revokeByTokenHashIfActive(tokenHash)
            refreshTokenCacheRepository.evict(tokenHash, cachedUserId)

            if (updatedRows == 1) {
                return userService.getById(cachedUserId)
            }
        }

        return rotateFromDatabase(tokenHash)
    }

    private fun rotateFromDatabase(tokenHash: String): UserInfo {
        val token = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow { CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND) }

        if (token.revoked) {
            revokeAllByUser(token.userId)
            throw CustomException(ErrorCode.REFRESH_TOKEN_REUSED)
        }

        token.validateUsable()
        token.revoke()
        refreshTokenCacheRepository.evict(tokenHash, token.userId)

        return userService.getById(token.userId)
    }

    @Transactional(noRollbackFor = [CustomException::class])
    fun revokeAllByUser(userId: Long) {
        refreshTokenRepository.revokeAllByUserId(userId)
        refreshTokenCacheRepository.evictAllByUser(userId)
    }

    @Transactional
    fun logout(rawRefreshToken: String) {
        val tokenHash = tokenHashUtil.sha256(rawRefreshToken)
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent { token ->
            token.revoke()
            refreshTokenCacheRepository.evict(tokenHash, token.userId)
        }
    }

    @Transactional
    fun cleanupStaleTokens(cutoff: LocalDateTime): Int {
        return refreshTokenRepository.deleteAllExpiredBefore(cutoff)
    }
}
