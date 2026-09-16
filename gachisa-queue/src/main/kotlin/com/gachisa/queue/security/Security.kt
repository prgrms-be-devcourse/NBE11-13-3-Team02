package com.gachisa.queue.security

import com.gachisa.queue.core.QueueProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.reactor.mono
import org.springframework.http.HttpHeaders
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
        .verifyWith(Keys.hmacShaKeyFor(secretBytesOf(properties.jwtSecret)))
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

    /**
     * 레이트 리밋 키를 뽑을 때만 쓴다. 토큰이 없거나 잘못돼도 예외를 던지지 않고 null을
     * 돌려준다 — 인증 실패 자체는 컨트롤러의 [userIdOf]가 판단할 몫이고, 여기서는
     * "가능하면 사용자 단위로, 안 되면 IP 단위로" 키를 정하는 데만 쓰인다.
     */
    fun lenientUserIdOf(authorizationHeader: String?): Long? {
        val token = authorizationHeader
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?: return null
        return try {
            parser.parseSignedClaims(token).payload.subject.toLong()
        } catch (e: Exception) {
            null
        }
    }

    private fun unauthorized(reason: String) =
        ResponseStatusException(HttpStatus.UNAUTHORIZED, reason)

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}

/**
 * 시크릿이 설정되지 않았을 때 원인을 알 수 있는 메시지로 바꿔 준다.
 *
 * 설정을 빠뜨리면 jjwt가 "key byte array is N bits"라는 암호학 용어로만 실패해서,
 * 정작 고쳐야 할 곳(공유 시크릿 설정)이 드러나지 않는다.
 */
private fun secretBytesOf(secret: String): ByteArray {
    val bytes = secret.toByteArray()
    require(bytes.size >= MIN_SECRET_BYTES) {
        "queue.jwt-secret 이 ${bytes.size}바이트입니다. core(gachisa-backend)의 jwt.secret 과 " +
            "같은 값을 application-local.yml 또는 JWT_SECRET 환경변수로 넣어주세요 " +
            "(최소 ${MIN_SECRET_BYTES}바이트). ./setup.sh 를 실행하면 자동으로 채워집니다."
    }
    return bytes
}

private const val MIN_SECRET_BYTES = 32

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

/**
 * 공개 API(api 로 시작하는 경로)를 토큰 버킷으로 보호한다. internal 경로는 이미 공유
 * 시크릿으로 잠겨 있고 core만 호출하므로 대상이 아니다.
 *
 * 용량과 보충 속도는 실제 트래픽 패턴에서 골랐다. 프론트엔드가 결제 대기 중
 * [GroupBuyCheckoutPage.waitForAdmission] 에서 1초 간격으로 상태를 폴링하므로,
 * 정상적인 사용자는 초당 1회 정도를 오래 지속한다. 버킷 용량 5는 토큰 발급 +
 * 몇 번의 폴링이 몰려도 첫 화면 진입에서 막히지 않을 여유이고, 보충 속도 1.2/초는
 * 그 폴링을 계속 허용하면서도 짧은 시간에 수십 번씩 두드리는 남용은 막는다.
 *
 * 사용자별로 버킷을 하나씩 메모리에 들고 있다. 인스턴스가 하나뿐인 지금 구성에서는
 * 문제가 없지만, 여러 인스턴스로 늘리면 사용자가 어느 인스턴스로 가느냐에 따라
 * 한도가 갈라진다 — 그때는 Redis 같은 공유 저장소로 옮겨야 한다.
 */
@Component
class RateLimitFilter(
    private val properties: QueueProperties,
    private val tokenVerifier: AccessTokenVerifier,
) : WebFilter {

    private val buckets = ConcurrentHashMap<String, TokenBucket>()

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange)
        }

        val bucket = buckets.computeIfAbsent(rateLimitKey(exchange)) {
            TokenBucket(properties.rateLimit.capacity, properties.rateLimit.refillPerSecond)
        }

        return mono { bucket.tryConsume() }.flatMap { allowed ->
            if (allowed) {
                chain.filter(exchange)
            } else {
                exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                exchange.response.headers.add(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                exchange.response.setComplete()
            }
        }
    }

    /** 로그인한 사용자면 사용자 단위로, 토큰이 없거나 잘못됐으면 IP 단위로 제한한다. */
    private fun rateLimitKey(exchange: ServerWebExchange): String {
        val userId = tokenVerifier.lenientUserIdOf(
            exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        )
        if (userId != null) return "user:$userId"

        val ip = exchange.request.remoteAddress?.address?.hostAddress
        return if (ip != null) "ip:$ip" else "unknown"
    }

    private companion object {
        const val RETRY_AFTER_SECONDS = "1"
    }
}
