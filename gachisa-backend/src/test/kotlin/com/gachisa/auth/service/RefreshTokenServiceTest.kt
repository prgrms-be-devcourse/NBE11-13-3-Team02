package com.gachisa.auth.service

import com.gachisa.auth.repository.RefreshTokenCacheRepository
import com.gachisa.auth.repository.RefreshTokenRepository
import com.gachisa.global.security.JwtProperties
import com.gachisa.global.security.TokenHashUtil
import com.gachisa.user.service.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RefreshTokenServiceTest {

    private val refreshTokenRepository: RefreshTokenRepository = mockk()
    private val refreshTokenCacheRepository: RefreshTokenCacheRepository = mockk()
    private val userService: UserService = mockk()
    private val tokenHashUtil: TokenHashUtil = mockk()
    private val jwtProperties: JwtProperties = mockk()

    private lateinit var refreshTokenService: RefreshTokenService

    @BeforeEach
    fun setUp() {
        refreshTokenService = RefreshTokenService(
            refreshTokenRepository, refreshTokenCacheRepository, userService, tokenHashUtil, jwtProperties,
        )
    }

    @Test
    fun cleanupStaleTokensDelegatesToRepositoryAndReturnsDeletedCount() {
        every { refreshTokenRepository.deleteAllExpiredBefore(CUTOFF) } returns 5

        val deletedCount = refreshTokenService.cleanupStaleTokens(CUTOFF)

        assertThat(deletedCount).isEqualTo(5)
        verify { refreshTokenRepository.deleteAllExpiredBefore(CUTOFF) }
    }

    companion object {
        private val CUTOFF: LocalDateTime = LocalDateTime.of(2026, 7, 25, 4, 0)
    }
}
