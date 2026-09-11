package com.gachisa.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.gachisa.auth.repository.RefreshTokenCacheRepository;
import com.gachisa.auth.repository.RefreshTokenRepository;
import com.gachisa.global.security.JwtProperties;
import com.gachisa.global.security.TokenHashUtil;
import com.gachisa.user.service.UserService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 7, 25, 4, 0);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenCacheRepository refreshTokenCacheRepository;

    @Mock
    private UserService userService;

    @Mock
    private TokenHashUtil tokenHashUtil;

    @Mock
    private JwtProperties jwtProperties;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository, refreshTokenCacheRepository, userService, tokenHashUtil, jwtProperties);
    }

    @Test
    void cleanupStaleTokensDelegatesToRepositoryAndReturnsDeletedCount() {
        given(refreshTokenRepository.deleteAllExpiredBefore(CUTOFF)).willReturn(5);

        int deletedCount = refreshTokenService.cleanupStaleTokens(CUTOFF);

        assertThat(deletedCount).isEqualTo(5);
        verify(refreshTokenRepository).deleteAllExpiredBefore(CUTOFF);
    }
}
