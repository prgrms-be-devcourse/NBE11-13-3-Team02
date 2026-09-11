package com.gachisa.auth.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenCacheRepository {

    private static final String TOKEN_KEY_PREFIX = "refresh:token:";
    private static final String USER_TOKENS_KEY_PREFIX = "refresh:user:";
    private static final String USER_TOKENS_KEY_SUFFIX = ":tokens";

    private final StringRedisTemplate redisTemplate;

    public void save(String tokenHash, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(tokenKey(tokenHash), String.valueOf(userId), ttl);

        String userTokensKey = userTokensKey(userId);
        redisTemplate.opsForSet().add(userTokensKey, tokenHash);
        redisTemplate.expire(userTokensKey, ttl);
    }

    public Optional<Long> findUserId(String tokenHash) {
        String value = redisTemplate.opsForValue().get(tokenKey(tokenHash));
        return value != null ? Optional.of(Long.valueOf(value)) : Optional.empty();
    }

    public void evict(String tokenHash, Long userId) {
        redisTemplate.delete(tokenKey(tokenHash));
        redisTemplate.opsForSet().remove(userTokensKey(userId), tokenHash);
    }

    public void evictAllByUser(Long userId) {
        String userTokensKey = userTokensKey(userId);
        Set<String> tokenHashes = redisTemplate.opsForSet().members(userTokensKey);
        if (tokenHashes != null && !tokenHashes.isEmpty()) {
            tokenHashes.forEach(hash -> redisTemplate.delete(tokenKey(hash)));
        }
        redisTemplate.delete(userTokensKey);
    }

    private String tokenKey(String tokenHash) {
        return TOKEN_KEY_PREFIX + tokenHash;
    }

    private String userTokensKey(Long userId) {
        return USER_TOKENS_KEY_PREFIX + userId + USER_TOKENS_KEY_SUFFIX;
    }
}
