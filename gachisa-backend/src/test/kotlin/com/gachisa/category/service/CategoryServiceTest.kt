package com.gachisa.category.service

import com.gachisa.category.entity.Category
import com.gachisa.category.repository.CategoryRepository
import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

class CategoryServiceTest {

    private val categoryRepository: CategoryRepository = mockk()
    private lateinit var categoryService: CategoryService

    @BeforeEach
    fun setUp() {
        categoryService = CategoryService(categoryRepository)
    }

    @Test
    fun getCategoriesReturnsTopLevelCategoriesWithChildren() {
        val root = category(ROOT_ID, "생활/리빙", null)
        val child = category(CHILD_ID, "주방용품", root)
        root.children.add(child)
        every { categoryRepository.findByParentIsNull() } returns listOf(root)

        val responses = categoryService.getCategories()

        assertThat(responses).hasSize(1)
        assertThat(responses[0].id).isEqualTo(ROOT_ID)
        assertThat(responses[0].children).hasSize(1)
        assertThat(responses[0].children[0].id).isEqualTo(CHILD_ID)
    }

    @Test
    fun getCategoryReturnsCategory() {
        val category = category(ROOT_ID, "생활/리빙", null)
        every { categoryRepository.findById(ROOT_ID) } returns Optional.of(category)

        val response = categoryService.getCategory(ROOT_ID)

        assertThat(response.id).isEqualTo(ROOT_ID)
        assertThat(response.name).isEqualTo("생활/리빙")
    }

    @Test
    fun getCategoryThrowsWhenNotFound() {
        every { categoryRepository.findById(ROOT_ID) } returns Optional.empty()

        assertThatThrownBy { categoryService.getCategory(ROOT_ID) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND)
    }

    @Test
    fun createCategoryCreatesTopLevelCategoryWhenParentIdIsNull() {
        every { categoryRepository.existsByName("뷰티") } returns false
        every { categoryRepository.save(any()) } answers { firstArg() }

        val response = categoryService.createCategory("뷰티", null)

        assertThat(response.name).isEqualTo("뷰티")
        assertThat(response.parentId).isNull()
    }

    @Test
    fun createCategoryCreatesChildCategoryWhenParentIdProvided() {
        val parent = category(ROOT_ID, "생활/리빙", null)
        every { categoryRepository.existsByName("주방용품") } returns false
        every { categoryRepository.findById(ROOT_ID) } returns Optional.of(parent)
        every { categoryRepository.save(any()) } answers { firstArg() }

        val response = categoryService.createCategory("주방용품", ROOT_ID)

        assertThat(response.parentId).isEqualTo(ROOT_ID)
    }

    @Test
    fun createCategoryThrowsWhenNameDuplicated() {
        every { categoryRepository.existsByName("뷰티") } returns true

        assertThatThrownBy { categoryService.createCategory("뷰티", null) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.CATEGORY_NAME_DUPLICATED)
    }

    @Test
    fun createCategoryThrowsWhenParentNotFound() {
        every { categoryRepository.existsByName("주방용품") } returns false
        every { categoryRepository.findById(ROOT_ID) } returns Optional.empty()

        assertThatThrownBy { categoryService.createCategory("주방용품", ROOT_ID) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND)
    }

    @Test
    fun updateCategoryChangesName() {
        val category = category(ROOT_ID, "생활/리빙", null)
        every { categoryRepository.findById(ROOT_ID) } returns Optional.of(category)
        every { categoryRepository.existsByName("리빙/생활") } returns false

        val response = categoryService.updateCategory(ROOT_ID, "리빙/생활")

        assertThat(response.name).isEqualTo("리빙/생활")
    }

    @Test
    fun updateCategoryAllowsKeepingSameName() {
        val category = category(ROOT_ID, "생활/리빙", null)
        every { categoryRepository.findById(ROOT_ID) } returns Optional.of(category)

        val response = categoryService.updateCategory(ROOT_ID, "생활/리빙")

        assertThat(response.name).isEqualTo("생활/리빙")
        verify(exactly = 0) { categoryRepository.existsByName(any()) }
    }

    @Test
    fun updateCategoryThrowsWhenNewNameDuplicated() {
        val category = category(ROOT_ID, "생활/리빙", null)
        every { categoryRepository.findById(ROOT_ID) } returns Optional.of(category)
        every { categoryRepository.existsByName("식품") } returns true

        assertThatThrownBy { categoryService.updateCategory(ROOT_ID, "식품") }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.CATEGORY_NAME_DUPLICATED)
    }

    @Test
    fun deleteCategoryDeletesWhenNoChildren() {
        val category = category(ROOT_ID, "생활/리빙", null)
        every { categoryRepository.findById(ROOT_ID) } returns Optional.of(category)
        every { categoryRepository.delete(category) } returns Unit

        categoryService.deleteCategory(ROOT_ID)

        verify { categoryRepository.delete(category) }
    }

    @Test
    fun deleteCategoryThrowsWhenHasChildren() {
        val category = category(ROOT_ID, "생활/리빙", null)
        category.children.add(category(CHILD_ID, "주방용품", category))
        every { categoryRepository.findById(ROOT_ID) } returns Optional.of(category)

        assertThatThrownBy { categoryService.deleteCategory(ROOT_ID) }
            .isInstanceOf(CustomException::class.java)
            .extracting { (it as CustomException).errorCode }
            .isEqualTo(ErrorCode.CATEGORY_HAS_CHILDREN)
        verify(exactly = 0) { categoryRepository.delete(any()) }
    }

    private fun category(id: Long, name: String, parent: Category?): Category {
        val category = Category.of(name, parent)
        ReflectionTestUtils.setField(category, "id", id)
        return category
    }

    companion object {
        private const val ROOT_ID = 1L
        private const val CHILD_ID = 2L
    }
}
