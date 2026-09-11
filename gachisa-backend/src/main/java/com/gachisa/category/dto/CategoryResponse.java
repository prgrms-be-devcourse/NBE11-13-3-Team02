package com.gachisa.category.dto;

import com.gachisa.category.entity.Category;
import java.util.List;
import java.util.stream.Collectors;

public record CategoryResponse(
    Long id,
    String name,
    Long parentId,
    List<CategoryResponse> children
) {
    public static CategoryResponse from(Category category) {
        Long parentId = category.getParent() != null ? category.getParent().getId() : null;
        List<CategoryResponse> children = category.getChildren().stream()
            .map(CategoryResponse::from)
            .collect(Collectors.toList());
        return new CategoryResponse(category.getId(), category.getName(), parentId, children);
    }
}
