package com.gachisa.auth.service;

import com.gachisa.auth.entity.RefreshToken;
import com.gachisa.auth.repository.RefreshTokenCacheRepository;
import com.gachisa.auth.repository.RefreshTokenRepository;
import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.global.security.JwtProperties;
import com.gachisa.global.security.TokenHashUtil;
import com.gachisa.user.dto.UserInfo;
import com.gachisa.user.service.UserService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCacheRepository refreshTokenCacheRepository;
    private final UserService userService;
    private final TokenHashUtil tokenHashUtil;
    private final JwtProperties jwtProperties;

    @Transactional
    public String issue(UserInfo user) {
        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = tokenHashUtil.sha256(rawRefreshToken);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(jwtProperties.refreshTokenValidity());

        RefreshToken refreshToken = RefreshToken.builder()
            .userId(user.id())
            .tokenHash(tokenHash)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .build();
        refreshTokenRepository.save(refreshToken);
        refreshTokenCacheRepository.save(tokenHash, user.id(), jwtProperties.refreshTokenValidity());

        return rawRefreshToken;
    }

    @Transactional(noRollbackFor = CustomException.class)
    public UserInfo rotate(String rawRefreshToken) {
        String tokenHash = tokenHashUtil.sha256(rawRefreshToken);

        Optional<Long> cachedUserId = refreshTokenCacheRepository.findUserId(tokenHash);
        if (cachedUserId.isPresent()) {
            Long userId = cachedUserId.get();
            int updatedRows = refreshTokenRepository.revokeByTokenHashIfActive(tokenHash);
            refreshTokenCacheRepository.evict(tokenHash, userId);

            if (updatedRows == 1) {
                return userService.getById(userId);
            }
        }

        return rotateFromDatabase(tokenHash);
    }

    private UserInfo rotateFromDatabase(String tokenHash) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (token.isRevoked()) {
            revokeAllByUser(token.getUserId());
            throw new CustomException(ErrorCode.REFRESH_TOKEN_REUSED);
        }

        token.validateUsable();
        token.revoke();
        refreshTokenCacheRepository.evict(tokenHash, token.getUserId());

        return userService.getById(token.getUserId());
    }

    @Transactional(noRollbackFor = CustomException.class)
    public void revokeAllByUser(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        refreshTokenCacheRepository.evictAllByUser(userId);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = tokenHashUtil.sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
            .ifPresent(token -> {
                token.revoke();
                refreshTokenCacheRepository.evict(tokenHash, token.getUserId());
            });
    }

    @Transactional
    public int cleanupStaleTokens(LocalDateTime cutoff) {
        return refreshTokenRepository.deleteAllExpiredBefore(cutoff);
    }
}
