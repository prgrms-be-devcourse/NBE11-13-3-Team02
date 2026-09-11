package com.gachisa.groupbuy.dto;

import com.gachisa.groupbuy.entity.GroupBuy;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class GroupBuyDetailResponse {

    private final Long groupBuyId;
    private final Long productId;
    private final String productName;
    private final BigDecimal discountRate;
    private final Integer currentCount;
    private final Integer targetCount;
    private final double progressRate;
    private final long remainingSeconds;
    private final LocalDateTime openAt;
    private final LocalDateTime deadline;
    private final String status;

    private GroupBuyDetailResponse(GroupBuy g, long remainingSeconds) {
        this.groupBuyId = g.getId();
        this.productId = g.getProduct().getId();
        this.productName = g.getProduct().getName();
        this.discountRate = g.getDiscountRate();
        this.currentCount = g.getCurrentCount();
        this.targetCount = g.getTargetCount();
        this.progressRate = g.getProgressRate();
        this.remainingSeconds = remainingSeconds;
        this.openAt = g.getOpenAt();
        this.deadline = g.getDeadline();
        this.status = g.getStatus().getLabel();
    }

    public static GroupBuyDetailResponse of(GroupBuy groupBuy, long remainingSeconds) {
        return new GroupBuyDetailResponse(groupBuy, remainingSeconds);
    }
}
