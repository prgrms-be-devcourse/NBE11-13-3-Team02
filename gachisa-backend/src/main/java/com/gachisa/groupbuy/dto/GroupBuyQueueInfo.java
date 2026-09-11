package com.gachisa.groupbuy.dto;

import com.gachisa.groupbuy.entity.GroupBuyStatus;
import java.time.LocalDateTime;

public record GroupBuyQueueInfo(
        Long groupBuyId,
        int targetCount,
        int currentCount,
        LocalDateTime openAt,
        LocalDateTime deadline,
        GroupBuyStatus status
) {
    public boolean isOpen(LocalDateTime now) {
        return status == GroupBuyStatus.RECRUITING
                && !now.isBefore(openAt)
                && now.isBefore(deadline);
    }

    public int remainingCount() {
        return Math.max(targetCount - currentCount, 0);
    }
}
