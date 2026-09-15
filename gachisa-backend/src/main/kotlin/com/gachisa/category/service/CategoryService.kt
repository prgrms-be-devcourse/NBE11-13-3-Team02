package com.gachisa.category.service

import com.gachisa.category.dto.CategoryResponse
import com.gachisa.category.entity.Category
import com.gachisa.category.repository.CategoryRepository
import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CategoryService(
    private val categoryRepository: CategoryRepository,
) {

    fun getCategories(): List<CategoryResponse> {
        return categoryRepository.findByParentIsNull().map { CategoryResponse.from(it) }
    }

    fun getCategory(categoryId: Long): CategoryResponse {
        val category = categoryRepository.findById(categoryId)
            .orElseThrow { CustomException(ErrorCode.CATEGORY_NOT_FOUND) }
        return CategoryResponse.from(category)
    }

    @Transactional
    fun createCategory(name: String, parentId: Long?): CategoryResponse {
        if (categoryRepository.existsByName(name)) {
            throw CustomException(ErrorCode.CATEGORY_NAME_DUPLICATED)
        }

        val parent = parentId?.let {
            categoryRepository.findById(it)
                .orElseThrow { CustomException(ErrorCode.CATEGORY_NOT_FOUND) }
        }

        val category = Category.of(name, parent)

        return CategoryResponse.from(categoryRepository.save(category))
    }

    @Transactional
    fun updateCategory(categoryId: Long, name: String): CategoryResponse {
        val category = categoryRepository.findById(categoryId)
            .orElseThrow { CustomException(ErrorCode.CATEGORY_NOT_FOUND) }

        if (category.name != name && categoryRepository.existsByName(name)) {
            throw CustomException(ErrorCode.CATEGORY_NAME_DUPLICATED)
        }

        category.updateName(name)
        return CategoryResponse.from(category)
    }

    @Transactional
    fun deleteCategory(categoryId: Long) {
        val category = categoryRepository.findById(categoryId)
            .orElseThrow { CustomException(ErrorCode.CATEGORY_NOT_FOUND) }

        if (category.children.isNotEmpty()) {
            throw CustomException(ErrorCode.CATEGORY_HAS_CHILDREN)
        }

        categoryRepository.delete(category)
    }
}
