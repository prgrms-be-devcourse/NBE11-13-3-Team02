package com.gachisa.participation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ParticipationStatus {

    PARTICIPATING("참여중"),
    CONFIRMED("확정"),
    REFUNDED("환불됨"),
    CANCELLED("취소됨");

    private final String label;
}
