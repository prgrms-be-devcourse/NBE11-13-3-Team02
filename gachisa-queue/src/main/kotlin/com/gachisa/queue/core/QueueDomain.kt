package com.gachisa.queue.core

import java.time.Duration
import java.time.LocalDateTime
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

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

/** core의 ErrorCode와 같은 이름을 쓴다. 프론트엔드가 분기하는 값이라 바뀌면 안 된다. */
enum class QueueError(val status: HttpStatus, val message: String) {
    QUEUE_NOT_OPEN(HttpStatus.BAD_REQUEST, "대기열을 사용할 수 없는 공동구매입니다."),
    QUEUE_TOKEN_INVALID(HttpStatus.FORBIDDEN, "유효하지 않은 대기열 토큰입니다."),
    QUEUE_ADMISSION_REQUIRED(HttpStatus.FORBIDDEN, "대기열 입장이 필요합니다."),
    QUEUE_ADMISSION_EXPIRED(HttpStatus.GONE, "대기열 입장이 만료되었습니다."),
    ;

    fun toException() = ResponseStatusException(status, name)
}

@ConfigurationProperties(prefix = "queue")
data class QueueProperties(
    val jwtSecret: String,
    val internalToken: String,
    val coreBaseUrl: String,
    val admissionTimeout: Duration = Duration.ofMinutes(10),
    val admissionBatchSize: Int = 10,
)
