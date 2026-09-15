package com.gachisa.category.dto

import com.gachisa.category.entity.Category

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
