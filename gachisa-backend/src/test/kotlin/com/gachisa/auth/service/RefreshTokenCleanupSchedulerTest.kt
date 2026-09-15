package com.gachisa.auth.service

import com.gachisa.global.util.TimeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RefreshTokenCleanupSchedulerTest {

    private val refreshTokenService: RefreshTokenService = mockk()
    private val timeProvider: TimeProvider = mockk()

    private lateinit var scheduler: RefreshTokenCleanupScheduler

    @BeforeEach
    fun setUp() {
        scheduler = RefreshTokenCleanupScheduler(refreshTokenService, timeProvider)
    }

    @Test
    fun cleansUpTokensExpiredMoreThan30DaysAgo() {
        every { timeProvider.now() } returns NOW
        every { refreshTokenService.cleanupStaleTokens(NOW.minusDays(30)) } returns 3

        scheduler.cleanupStaleTokens()

        verify { refreshTokenService.cleanupStaleTokens(NOW.minusDays(30)) }
    }

    companion object {
        private val NOW: LocalDateTime = LocalDateTime.of(2026, 8, 24, 4, 0)
    }
}
