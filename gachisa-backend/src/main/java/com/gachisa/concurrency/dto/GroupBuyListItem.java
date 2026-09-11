package com.gachisa.concurrency.dto;

import com.gachisa.groupbuy.entity.GroupBuyStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * 동시성 데모 페이지에서 "테스트할 공동구매"를 드롭다운으로 고를 수 있도록
 * DB에 있는 GroupBuy 목록을 최소 정보만 담아 내려주는 DTO.
 */
@Getter
@Builder
public class GroupBuyListItem {
    private final Long id;
    private final String productName;
    private final GroupBuyStatus status;
    private final int currentCount;
    private final int targetCount;
}
