package com.gachisa.category.dto

import com.gachisa.category.entity.Category
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class CategoryCreateRequest(
    @field:Schema(description = "카테고리명 (중복 불가)", example = "생활/리빙")
    @field:NotBlank
    val name: String,
    @field:Schema(description = "상위 카테고리 ID. 최상위 카테고리로 만들려면 생략/null", example = "1")
    val parentId: Long?,
)

data class CategoryResponse(
    val id: Long?,
    val name: String,
    val parentId: Long?,
    val children: List<CategoryResponse>,
) {
    companion object {
        fun from(category: Category): CategoryResponse {
            return CategoryResponse(
                id = category.id,
                name = category.name,
                parentId = category.parent?.id,
                children = category.children.map { from(it) },
            )
        }
    }
}

data class CategoryUpdateRequest(
    @field:Schema(description = "변경할 카테고리명 (중복 불가)", example = "생활용품")
    @field:NotBlank
    val name: String,
)
