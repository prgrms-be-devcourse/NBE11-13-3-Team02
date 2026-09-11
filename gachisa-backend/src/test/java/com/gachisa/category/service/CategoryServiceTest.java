package com.gachisa.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachisa.category.dto.CategoryResponse;
import com.gachisa.category.entity.Category;
import com.gachisa.category.repository.CategoryRepository;
import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long ROOT_ID = 1L;
    private static final Long CHILD_ID = 2L;

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);
    }

    @Test
    void getCategoriesReturnsTopLevelCategoriesWithChildren() {
        Category root = category(ROOT_ID, "생활/리빙", null);
        Category child = category(CHILD_ID, "주방용품", root);
        root.getChildren().add(child);
        given(categoryRepository.findByParentIsNull()).willReturn(List.of(root));

        List<CategoryResponse> responses = categoryService.getCategories();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(ROOT_ID);
        assertThat(responses.get(0).children()).hasSize(1);
        assertThat(responses.get(0).children().get(0).id()).isEqualTo(CHILD_ID);
    }

    @Test
    void getCategoryReturnsCategory() {
        Category category = category(ROOT_ID, "생활/리빙", null);
        given(categoryRepository.findById(ROOT_ID)).willReturn(Optional.of(category));

        CategoryResponse response = categoryService.getCategory(ROOT_ID);

        assertThat(response.id()).isEqualTo(ROOT_ID);
        assertThat(response.name()).isEqualTo("생활/리빙");
    }

    @Test
    void getCategoryThrowsWhenNotFound() {
        given(categoryRepository.findById(ROOT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategory(ROOT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void createCategoryCreatesTopLevelCategoryWhenParentIdIsNull() {
        given(categoryRepository.existsByName("뷰티")).willReturn(false);
        given(categoryRepository.save(org.mockito.ArgumentMatchers.any(Category.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.createCategory("뷰티", null);

        assertThat(response.name()).isEqualTo("뷰티");
        assertThat(response.parentId()).isNull();
    }

    @Test
    void createCategoryCreatesChildCategoryWhenParentIdProvided() {
        Category parent = category(ROOT_ID, "생활/리빙", null);
        given(categoryRepository.existsByName("주방용품")).willReturn(false);
        given(categoryRepository.findById(ROOT_ID)).willReturn(Optional.of(parent));
        given(categoryRepository.save(org.mockito.ArgumentMatchers.any(Category.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.createCategory("주방용품", ROOT_ID);

        assertThat(response.parentId()).isEqualTo(ROOT_ID);
    }

    @Test
    void createCategoryThrowsWhenNameDuplicated() {
        given(categoryRepository.existsByName("뷰티")).willReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory("뷰티", null))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NAME_DUPLICATED);
    }

    @Test
    void createCategoryThrowsWhenParentNotFound() {
        given(categoryRepository.existsByName("주방용품")).willReturn(false);
        given(categoryRepository.findById(ROOT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.createCategory("주방용품", ROOT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void updateCategoryChangesName() {
        Category category = category(ROOT_ID, "생활/리빙", null);
        given(categoryRepository.findById(ROOT_ID)).willReturn(Optional.of(category));
        given(categoryRepository.existsByName("리빙/생활")).willReturn(false);

        CategoryResponse response = categoryService.updateCategory(ROOT_ID, "리빙/생활");

        assertThat(response.name()).isEqualTo("리빙/생활");
    }

    @Test
    void updateCategoryAllowsKeepingSameName() {
        Category category = category(ROOT_ID, "생활/리빙", null);
        given(categoryRepository.findById(ROOT_ID)).willReturn(Optional.of(category));

        CategoryResponse response = categoryService.updateCategory(ROOT_ID, "생활/리빙");

        assertThat(response.name()).isEqualTo("생활/리빙");
        verify(categoryRepository, never()).existsByName(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateCategoryThrowsWhenNewNameDuplicated() {
        Category category = category(ROOT_ID, "생활/리빙", null);
        given(categoryRepository.findById(ROOT_ID)).willReturn(Optional.of(category));
        given(categoryRepository.existsByName("식품")).willReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(ROOT_ID, "식품"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NAME_DUPLICATED);
    }

    @Test
    void deleteCategoryDeletesWhenNoChildren() {
        Category category = category(ROOT_ID, "생활/리빙", null);
        given(categoryRepository.findById(ROOT_ID)).willReturn(Optional.of(category));

        categoryService.deleteCategory(ROOT_ID);

        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteCategoryThrowsWhenHasChildren() {
        Category category = category(ROOT_ID, "생활/리빙", null);
        category.getChildren().add(category(CHILD_ID, "주방용품", category));
        given(categoryRepository.findById(ROOT_ID)).willReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.deleteCategory(ROOT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_HAS_CHILDREN);
        verify(categoryRepository, never()).delete(org.mockito.ArgumentMatchers.any(Category.class));
    }

    private Category category(Long id, String name, Category parent) {
        Category category = Category.builder()
                .name(name)
                .parent(parent)
                .build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
