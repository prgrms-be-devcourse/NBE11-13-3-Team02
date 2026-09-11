package com.gachisa.concurrency.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "동시성 데모(local 전용)용 부하 테스트 요청")
public class ConcurrencyStressRequest {

    public enum Mode {
        /** read-check-write (락/Redis 없음) — 초과 모집이 발생할 수 있음 */
        UNSAFE,
        /** DB SELECT FOR UPDATE 만 사용 */
        DB_LOCK,
        /** 분산락(Redis 원자 예약) + DB 비관적 락 (현재 운영 경로) */
        REDIS_AND_DB
    }

    @Schema(description = "동시성 제어 방식", example = "REDIS_AND_DB")
    @NotNull
    private Mode mode = Mode.REDIS_AND_DB;

    @Schema(description = "동시에 던질 요청 수 (2~80)", example = "30")
    @Min(2)
    @Max(80)
    private int threadCount = 30;

    @Schema(description = "요청당 예약 수량 (1~10)", example = "1")
    @Min(1)
    @Max(10)
    private int quantityPerRequest = 1;
}
