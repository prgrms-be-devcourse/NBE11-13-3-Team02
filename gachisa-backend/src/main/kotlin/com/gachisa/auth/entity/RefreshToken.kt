package com.gachisa.auth.entity

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "refresh_token")
class RefreshToken private constructor(
    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @Column(nullable = false)
    val issuedAt: LocalDateTime,

    @Column(nullable = false)
    val expiresAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false)
    var revoked: Boolean = false
        protected set

    fun revoke() {
        this.revoked = true
    }

    fun isUsable(): Boolean = !revoked && expiresAt.isAfter(LocalDateTime.now())

    fun validateUsable() {
        if (!isUsable()) {
            throw CustomException(ErrorCode.INVALID_REFRESH_TOKEN)
        }
    }

    companion object {
        @JvmStatic
        fun of(userId: Long, tokenHash: String, issuedAt: LocalDateTime, expiresAt: LocalDateTime): RefreshToken =
            RefreshToken(userId, tokenHash, issuedAt, expiresAt)
    }
}
