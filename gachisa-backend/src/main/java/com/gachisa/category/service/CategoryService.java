package com.gachisa.category.service;

import com.gachisa.category.dto.CategoryResponse;
import com.gachisa.category.entity.Category;
import com.gachisa.category.repository.CategoryRepository;
import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findByParentIsNull().stream()
            .map(CategoryResponse::from)
            .collect(Collectors.toList());
    }

    public CategoryResponse getCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse createCategory(String name, Long parentId) {
        if (categoryRepository.existsByName(name)) {
            throw new CustomException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }

        Category parent = null;
        if (parentId != null) {
            parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));
        }

        Category category = Category.builder()
            .name(name)
            .parent(parent)
            .build();

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long categoryId, String name) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        if (!category.getName().equals(name) && categoryRepository.existsByName(name)) {
            throw new CustomException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }

        category.updateName(name);
        return CategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        if (!category.getChildren().isEmpty()) {
            throw new CustomException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }

        categoryRepository.delete(category);
    }
}
