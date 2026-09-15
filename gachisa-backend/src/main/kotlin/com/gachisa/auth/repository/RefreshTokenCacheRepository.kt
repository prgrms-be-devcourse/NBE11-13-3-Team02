package com.gachisa.auth.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RefreshTokenCacheRepository(
    private val redisTemplate: StringRedisTemplate,
) {

    fun save(tokenHash: String, userId: Long, ttl: Duration) {
        redisTemplate.opsForValue().set(tokenKey(tokenHash), userId.toString(), ttl)

        val userTokensKey = userTokensKey(userId)
        redisTemplate.opsForSet().add(userTokensKey, tokenHash)
        redisTemplate.expire(userTokensKey, ttl)
    }

    fun findUserId(tokenHash: String): Long? {
        val value = redisTemplate.opsForValue().get(tokenKey(tokenHash))
        return value?.toLong()
    }

    fun evict(tokenHash: String, userId: Long) {
        redisTemplate.delete(tokenKey(tokenHash))
        redisTemplate.opsForSet().remove(userTokensKey(userId), tokenHash)
    }

    fun evictAllByUser(userId: Long) {
        val userTokensKey = userTokensKey(userId)
        val tokenHashes = redisTemplate.opsForSet().members(userTokensKey)
        if (!tokenHashes.isNullOrEmpty()) {
            tokenHashes.forEach { hash -> redisTemplate.delete(tokenKey(hash)) }
        }
        redisTemplate.delete(userTokensKey)
    }

    private fun tokenKey(tokenHash: String) = TOKEN_KEY_PREFIX + tokenHash

    private fun userTokensKey(userId: Long) = USER_TOKENS_KEY_PREFIX + userId + USER_TOKENS_KEY_SUFFIX

    companion object {
        private const val TOKEN_KEY_PREFIX = "refresh:token:"
        private const val USER_TOKENS_KEY_PREFIX = "refresh:user:"
        private const val USER_TOKENS_KEY_SUFFIX = ":tokens"
    }
}
