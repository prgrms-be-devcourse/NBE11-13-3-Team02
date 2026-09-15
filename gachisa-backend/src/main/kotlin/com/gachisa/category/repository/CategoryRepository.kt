package com.gachisa.category.repository

import com.gachisa.category.entity.Category
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<Category, Long> {

    fun findByParentIsNull(): List<Category>

    fun existsByName(name: String): Boolean
}
