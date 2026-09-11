package com.gachisa.auth.service;

import com.gachisa.global.util.TimeProvider;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private static final int RETENTION_DAYS = 30;

    private final RefreshTokenService refreshTokenService;
    private final TimeProvider timeProvider;

    @Scheduled(cron = "${auth.refresh-token-cleanup.cron:0 0 4 * * *}")
    public void cleanupStaleTokens() {
        LocalDateTime cutoff = timeProvider.now().minusDays(RETENTION_DAYS);
        int deletedCount = refreshTokenService.cleanupStaleTokens(cutoff);
        log.info("만료된 지 {}일 지난 리프레시 토큰 {}건을 삭제했습니다.", RETENTION_DAYS, deletedCount);
    }
}
