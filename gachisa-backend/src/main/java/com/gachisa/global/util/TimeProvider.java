package com.gachisa.global.util;

import java.time.LocalDateTime;

// 테스트에서 시간을 고정/제어하기 위한 추상화 (마감 배치 테스트 시 유용)
public interface TimeProvider {
    LocalDateTime now();
}
