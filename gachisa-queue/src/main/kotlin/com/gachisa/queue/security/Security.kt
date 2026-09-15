package com.gachisa.queue.security

import com.gachisa.queue.core.QueueProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * core와 같은 시크릿으로 액세스 토큰을 검증한다.
 *
 * 이 서비스는 토큰을 발급하지 않는다. 검증만 하고 userId를 꺼내 쓴다.
 */
@Component
class AccessTokenVerifier(properties: QueueProperties) {

    private val parser = Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(properties.jwtSecret.toByteArray()))
        .build()

    fun userIdOf(authorizationHeader: String?): Long {
        val token = authorizationHeader
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?: throw unauthorized("인증 토큰이 없습니다.")

        return try {
            parser.parseSignedClaims(token).payload.subject.toLong()
        } catch (e: Exception) {
            throw unauthorized("유효하지 않은 토큰입니다.")
        }
    }

    private fun unauthorized(reason: String) =
        ResponseStatusException(HttpStatus.UNAUTHORIZED, reason)

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}

/**
 * internal 경로는 core만 부를 수 있어야 한다. 브라우저에 열리면 남의 대기열을 조작할 수 있다.
 *
 * 네트워크 격리(같은 VPC/네임스페이스)가 1차 방어이고, 이 필터는 그게 뚫렸을 때의 2차 방어다.
 */
@Component
class InternalTokenFilter(private val properties: QueueProperties) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!exchange.request.path.value().startsWith("/internal/")) {
            return chain.filter(exchange)
        }
        val presented = exchange.request.headers.getFirst(INTERNAL_TOKEN_HEADER)
        if (presented == null || !constantTimeEquals(presented, properties.internalToken)) {
            exchange.response.statusCode = HttpStatus.FORBIDDEN
            return exchange.response.setComplete()
        }
        return chain.filter(exchange)
    }

    /** 길이와 내용 비교 시간이 값에 따라 달라지지 않게 한다. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        val left = a.toByteArray()
        val right = b.toByteArray()
        if (left.size != right.size) return false
        var diff = 0
        for (i in left.indices) diff = diff or (left[i].toInt() xor right[i].toInt())
        return diff == 0
    }

    private companion object {
        const val INTERNAL_TOKEN_HEADER = "X-Internal-Token"
    }
}
