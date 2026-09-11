package com.gachisa.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CategoryCreateRequest(
    @Schema(description = "카테고리명 (중복 불가)", example = "생활/리빙")
    @NotBlank String name,
    @Schema(description = "상위 카테고리 ID. 최상위 카테고리로 만들려면 생략/null", example = "1")
    Long parentId
) {}
