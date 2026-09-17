package com.gachisa.payment.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PgRetryPolicyTest {
    private val retryPolicy = PgRetryPolicy()
    private val now = LocalDateTime.of(2026, 8, 18, 12, 0)

    @Test
    fun retryDelayIncreasesToTenThirtyAndSixtySeconds() {
        assertThat(retryPolicy.nextRetryAt(now, 1)).isEqualTo(now.plusSeconds(10))
        assertThat(retryPolicy.nextRetryAt(now, 2)).isEqualTo(now.plusSeconds(30))
        assertThat(retryPolicy.nextRetryAt(now, 3)).isEqualTo(now.plusSeconds(60))
    }

    @Test
    fun threeRetriesExhaustAutomaticRecovery() {
        assertThat(retryPolicy.isExhausted(2)).isFalse()
        assertThat(retryPolicy.isExhausted(3)).isTrue()
    }
}
