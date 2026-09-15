package com.gachisa.auth.service

import com.gachisa.global.util.TimeProvider
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RefreshTokenCleanupScheduler(
    private val refreshTokenService: RefreshTokenService,
    private val timeProvider: TimeProvider,
) {

    @Scheduled(cron = "\${auth.refresh-token-cleanup.cron:0 0 4 * * *}")
    fun cleanupStaleTokens() {
        val cutoff = timeProvider.now().minusDays(RETENTION_DAYS)
        val deletedCount = refreshTokenService.cleanupStaleTokens(cutoff)
        log.info("만료된 지 {}일 지난 리프레시 토큰 {}건을 삭제했습니다.", RETENTION_DAYS, deletedCount)
    }

    companion object {
        private val log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler::class.java)
        private const val RETENTION_DAYS = 30L
    }
}
