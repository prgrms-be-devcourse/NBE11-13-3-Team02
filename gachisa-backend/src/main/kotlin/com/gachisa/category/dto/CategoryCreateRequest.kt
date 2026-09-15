package com.gachisa.category.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class CategoryCreateRequest(
    @field:Schema(description = "카테고리명 (중복 불가)", example = "생활/리빙")
    @field:NotBlank
    val name: String,
    @field:Schema(description = "상위 카테고리 ID. 최상위 카테고리로 만들려면 생략/null", example = "1")
    val parentId: Long?,
)
