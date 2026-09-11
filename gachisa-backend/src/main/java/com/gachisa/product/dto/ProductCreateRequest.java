package com.gachisa.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "상품 등록 요청 (multipart의 'data' 파트에 JSON으로 담아 전송)")
public record ProductCreateRequest(
    @Schema(description = "상품명 (같은 판매자 내에서 중복 불가)", example = "텀블러 6종 세트")
    @NotBlank String name,
    @Schema(description = "상품 설명", example = "보온·보냉 겸용 텀블러 6종 구성")
    String description,
    @Schema(description = "정가(원). 0보다 커야 함", example = "18000")
    @Positive int basePrice,
    @Schema(description = "재고 수량. 0 이상이어야 함", example = "100")
    @PositiveOrZero int stock,
    @Schema(description = "카테고리 ID", example = "1")
    @NotNull Long categoryId
) {}
