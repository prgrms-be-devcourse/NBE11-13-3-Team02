package com.gachisa.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CategoryUpdateRequest(
    @Schema(description = "변경할 카테고리명 (중복 불가)", example = "생활용품")
    @NotBlank String name
) {}
