package com.gachisa.participation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "공동구매 참여 요청")
public class ParticipationCreateRequest {

    @Schema(description = "참여 수량 (1 이상)", example = "1")
    @NotNull
    @Min(1)
    private Integer quantity;
}
