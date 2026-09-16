package com.gachisa.queue.core

import java.time.Duration
import java.time.LocalDateTime
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus

enum class QueueState { WAITING, ADMITTED, CONFIRMING }

data class QueueStatusResponse(
    val status: QueueState,
    val position: Long?,
    val admissionExpiresAt: LocalDateTime?,
)

data class QueueTokenResponse(
    val queueToken: String?,
    val status: QueueState,
    val position: Long?,
    val admissionExpiresAt: LocalDateTime?,
)

/** core에서 받아오는 공동구매 정보. 대기열 판단에 필요한 최소한만 가져온다. */
data class GroupBuyQueueInfo(
    val groupBuyId: Long,
    val targetCount: Int,
    val currentCount: Int,
    val openAt: LocalDateTime,
    val deadline: LocalDateTime,
    val status: String,
) {
    fun isOpen(now: LocalDateTime): Boolean =
        status == "RECRUITING" && !now.isBefore(openAt) && now.isBefore(deadline)

    val remainingCount: Int get() = (targetCount - currentCount).coerceAtLeast(0)
}

/**
 * core의 ErrorCode와 같은 이름을 쓴다. core가 이 이름으로 자기 예외를 복원하고
 * 프론트엔드가 그 값으로 분기하므로 바뀌면 안 된다.
 */
enum class QueueError(val status: HttpStatus, val message: String) {
    QUEUE_NOT_OPEN(HttpStatus.CONFLICT, "현재 대기열에 참여할 수 없습니다."),
    QUEUE_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 대기열 토큰입니다."),
    QUEUE_ADMISSION_REQUIRED(HttpStatus.CONFLICT, "결제 차례가 아직 도착하지 않았습니다."),
    QUEUE_ADMISSION_EXPIRED(HttpStatus.CONFLICT, "결제 가능 시간이 만료되었습니다."),
    ;

    fun toException() = QueueException(this)
}

class QueueException(val error: QueueError) : RuntimeException(error.name)

@ConfigurationProperties(prefix = "queue")
data class QueueProperties(
    val jwtSecret: String,
    val internalToken: String,
    val coreBaseUrl: String,
    val admissionTimeout: Duration = Duration.ofMinutes(10),
    val admissionBatchSize: Int = 10,
    val rateLimit: RateLimitProperties = RateLimitProperties(),
)

data class RateLimitProperties(
    // 프론트가 결제 대기 중 1초 간격으로 상태를 폴링한다([RateLimitFilter] 참고).
    // capacity는 토큰 발급과 폴링이 겹쳐도 막히지 않을 여유이고, refillPerSecond는
    // 그 폴링 속도를 웃돈다.
    val capacity: Double = 5.0,
    val refillPerSecond: Double = 1.2,
)
