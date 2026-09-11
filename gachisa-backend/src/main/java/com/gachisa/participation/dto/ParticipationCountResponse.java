package com.gachisa.participation.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ParticipationCountResponse {
    private final Integer currentCount;
    private final Integer targetCount;
}
