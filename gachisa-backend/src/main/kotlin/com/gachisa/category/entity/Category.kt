package com.gachisa.category.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "category")
class Category private constructor(
    @Column(nullable = false)
    var name: String,

    // 현재는 1단계까지만 사용하지만, 자기참조로 매핑해 계층 구조 확장이 가능하도록 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Category? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @OneToMany(mappedBy = "parent")
    var children: MutableList<Category> = mutableListOf()
        protected set

    fun updateName(name: String) {
        this.name = name
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun of(name: String, parent: Category? = null): Category = Category(name, parent)
    }
}
