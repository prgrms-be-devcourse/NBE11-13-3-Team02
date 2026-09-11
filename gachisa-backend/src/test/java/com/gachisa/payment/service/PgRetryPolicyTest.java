package com.gachisa.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PgRetryPolicyTest {

    private final PgRetryPolicy retryPolicy = new PgRetryPolicy();
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 18, 12, 0);

    @Test
    void retryDelayIncreasesToTenThirtyAndSixtySeconds() {
        assertThat(retryPolicy.nextRetryAt(now, 1)).isEqualTo(now.plusSeconds(10));
        assertThat(retryPolicy.nextRetryAt(now, 2)).isEqualTo(now.plusSeconds(30));
        assertThat(retryPolicy.nextRetryAt(now, 3)).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void threeRetriesExhaustAutomaticRecovery() {
        assertThat(retryPolicy.isExhausted(2)).isFalse();
        assertThat(retryPolicy.isExhausted(3)).isTrue();
    }
}
