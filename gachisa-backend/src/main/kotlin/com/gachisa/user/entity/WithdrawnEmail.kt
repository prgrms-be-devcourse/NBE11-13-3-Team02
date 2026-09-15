package com.gachisa.user.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

// User.email은 탈퇴 시 즉시 익명화되므로, 재가입 쿨다운을 판정하려면 원본 이메일을 별도로 남겨야 한다.
@Entity
@Table(name = "withdrawn_email")
class WithdrawnEmail private constructor(
    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    var withdrawnAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun renewWithdrawnAt(withdrawnAt: LocalDateTime) {
        this.withdrawnAt = withdrawnAt
    }

    companion object {
        @JvmStatic
        fun of(email: String, withdrawnAt: LocalDateTime): WithdrawnEmail = WithdrawnEmail(email, withdrawnAt)
    }
}
