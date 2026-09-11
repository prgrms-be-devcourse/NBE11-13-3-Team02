package com.gachisa.category.controller;

import com.gachisa.category.dto.CategoryCreateRequest;
import com.gachisa.category.dto.CategoryResponse;
import com.gachisa.category.dto.CategoryUpdateRequest;
import com.gachisa.category.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Category", description = "카테고리 조회/등록/수정/삭제. 등록/수정/삭제는 관리자 전용입니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "카테고리 목록 조회", description = "최상위 카테고리 목록을 하위 카테고리(children) 포함 트리 형태로 반환합니다. 인증 불필요.")
    @GetMapping
    public List<CategoryResponse> getCategories() {
        return categoryService.getCategories();
    }

    @Operation(summary = "카테고리 단건 조회", description = "인증 불필요.")
    @GetMapping("/{categoryId}")
    public CategoryResponse getCategory(@PathVariable Long categoryId) {
        return categoryService.getCategory(categoryId);
    }

    @Operation(summary = "카테고리 생성 (관리자 전용)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        return categoryService.createCategory(request.name(), request.parentId());
    }

    @Operation(summary = "카테고리명 수정 (관리자 전용)")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{categoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse updateCategory(@PathVariable Long categoryId, @Valid @RequestBody CategoryUpdateRequest request) {
        return categoryService.updateCategory(categoryId, request.name());
    }

    @Operation(summary = "카테고리 삭제 (관리자 전용)", description = "하위 카테고리가 있으면 삭제할 수 없습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteCategory(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
    }
}
