package com.gachisa.queue.security

import com.gachisa.queue.core.QueueProperties
import com.gachisa.queue.core.RateLimitProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class RateLimitFilterTest {

    private val properties = QueueProperties(
        jwtSecret = "x".repeat(64),
        internalToken = "internal",
        coreBaseUrl = "http://core.test",
        rateLimit = RateLimitProperties(capacity = 2.0, refillPerSecond = 0.0),
    )
    private val tokenVerifier = AccessTokenVerifier(properties)
    private val filter = RateLimitFilter(properties, tokenVerifier)
    private val passThroughChain = WebFilterChain { Mono.empty() }

    private fun exchangeFor(path: String) =
        MockServerWebExchange.from(MockServerHttpRequest.get(path).build())

    private fun exchangeWithAuth(userId: Long) = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/group-buys/1/queue-token")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + signToken(userId))
            .build()
    )

    /** AccessTokenVerifier가 받아들이는 형태의 서명된 토큰을 만든다. */
    private fun signToken(userId: Long): String =
        Jwts.builder()
            .subject(userId.toString())
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(properties.jwtSecret.toByteArray()))
            .compact()

    @Test
    fun `internal 경로는 제한하지 않는다`() = runTest {
        val exchange = exchangeFor("/internal/queues/1/users/1/complete")

        repeat(10) {
            filter.filter(exchange, passThroughChain).block()
        }

        assertNull(exchange.response.statusCode, "차단됐다면 상태 코드가 설정됐을 것이다")
    }

    @Test
    fun `용량을 넘으면 429와 Retry-After를 돌려준다`() = runTest {
        val exchange = exchangeFor("/api/group-buys/1/queue-token")

        filter.filter(exchange, passThroughChain).block()
        filter.filter(exchange, passThroughChain).block()
        filter.filter(exchange, passThroughChain).block()

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.response.statusCode)
        assertEquals("1", exchange.response.headers.getFirst(HttpHeaders.RETRY_AFTER))
    }

    @Test
    fun `다른 사용자는 서로의 한도에 영향받지 않는다`() = runTest {
        filter.filter(exchangeWithAuth(userId = 1L), passThroughChain).block()
        filter.filter(exchangeWithAuth(userId = 1L), passThroughChain).block()
        val blockedA = exchangeWithAuth(userId = 1L)
        filter.filter(blockedA, passThroughChain).block()
        val firstB = exchangeWithAuth(userId = 2L)
        filter.filter(firstB, passThroughChain).block()

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blockedA.response.statusCode)
        assertNull(firstB.response.statusCode, "다른 사용자의 첫 요청은 막히면 안 된다")
    }
}
