package com.gachisa.concurrency.dto;

import com.gachisa.concurrency.dto.ConcurrencyStressRequest.Mode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConcurrencyStressResponse {
    private final Mode mode;
    private final Long groupBuyId;
    private final int threadCount;
    private final int quantityPerRequest;
    private final int targetCount;
    private final int currentCountBefore;
    private final int currentCountAfterDb;
    private final Integer currentCountAfterRedis;
    private final int remainingSlotsAtStart;
    private final int successCount;
    private final int failureCount;
    private final int rejectedAsFull;
    private final int otherFailures;
    private final boolean oversold;
    private final String summary;
}
