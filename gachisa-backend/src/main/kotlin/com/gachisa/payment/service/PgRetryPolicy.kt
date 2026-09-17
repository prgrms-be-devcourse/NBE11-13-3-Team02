package com.gachisa.payment.service

import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class PgRetryPolicy {
    companion object {
        const val MAX_RETRY_COUNT = 3
    }

    fun nextRetryAt(now: LocalDateTime, retryCount: Int): LocalDateTime = when {
        retryCount <= 1 -> now.plusSeconds(10)
        retryCount == 2 -> now.plusSeconds(30)
        else -> now.plusSeconds(60)
    }

    fun isExhausted(retryCount: Int): Boolean = retryCount >= MAX_RETRY_COUNT
}
