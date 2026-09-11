package com.gachisa.groupbuy.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GroupBuyStatus {

    RECRUITING("모집중"),
    ACHIEVED("목표달성"),
    NOT_ACHIEVED("목표미달"),
    SETTLED("정산완료"),
    CANCELLED("취소됨");

    private final String label;
}
