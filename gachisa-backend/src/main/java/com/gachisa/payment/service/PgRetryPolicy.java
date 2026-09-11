package com.gachisa.payment.service;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class PgRetryPolicy {

    public static final int MAX_RETRY_COUNT = 3;

    public LocalDateTime nextRetryAt(LocalDateTime now, int retryCount) {
        if (retryCount <= 1) {
            return now.plusSeconds(10);
        }
        if (retryCount == 2) {
            return now.plusSeconds(30);
        }
        return now.plusSeconds(60);
    }

    public boolean isExhausted(int retryCount) {
        return retryCount >= MAX_RETRY_COUNT;
    }
}
