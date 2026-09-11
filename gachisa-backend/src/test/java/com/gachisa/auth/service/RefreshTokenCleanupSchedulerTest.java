package com.gachisa.auth.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachisa.global.util.TimeProvider;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 4, 0);

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TimeProvider timeProvider;

    private RefreshTokenCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RefreshTokenCleanupScheduler(refreshTokenService, timeProvider);
    }

    @Test
    void cleansUpTokensExpiredMoreThan30DaysAgo() {
        given(timeProvider.now()).willReturn(NOW);
        given(refreshTokenService.cleanupStaleTokens(NOW.minusDays(30))).willReturn(3);

        scheduler.cleanupStaleTokens();

        verify(refreshTokenService).cleanupStaleTokens(NOW.minusDays(30));
    }
}
