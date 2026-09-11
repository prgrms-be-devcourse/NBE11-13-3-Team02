package com.gachisa.groupbuy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Schema(description = "공동구매 생성 요청 (판매자 전용)")
public class GroupBuyCreateRequest {

    @Schema(description = "공동구매로 판매할 상품 ID", example = "1")
    @NotNull
    private Long productId;

    @Schema(description = "목표 인원(달성해야 할 참여 수량 합계)", example = "10")
    @NotNull
    @Min(1)
    private Integer targetCount;

    @Schema(description = "할인율 (0 초과 1 미만, 예: 0.3 = 30% 할인)", example = "0.3")
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax(value = "1.0", inclusive = false)
    private BigDecimal discountRate;

    @Schema(description = "모집 시작 시각", example = "2026-08-24T00:00:00")
    @NotNull
    private LocalDateTime openAt;

    @Schema(description = "모집 마감 시각 (모집 시작 시각 이후여야 함)", example = "2026-08-31T23:59:59")
    @NotNull
    private LocalDateTime deadline;
}
