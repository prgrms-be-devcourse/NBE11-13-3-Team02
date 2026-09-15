package com.gachisa.user.entity

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "users", // user는 MySQL 예약어라 테이블명은 users로 매핑
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "provider_id"])],
)
class User private constructor(
    @Column(nullable = false, unique = true)
    val email: String?,

    // 소셜 전용 계정은 비밀번호가 없을 수 있다 (nullable)
    var password: String?,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: UserRole,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var provider: UserProvider,

    @Column(name = "provider_id")
    var providerId: String?,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.ACTIVE,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    fun updateName(name: String) {
        this.name = name
    }

    fun changePassword(encodedPassword: String) {
        this.password = encodedPassword
    }

    fun linkOAuthAccount(provider: UserProvider, providerId: String?) {
        this.provider = provider
        this.providerId = providerId
    }

    fun suspend() {
        if (status == UserStatus.WITHDRAWN) {
            throw CustomException(ErrorCode.ACCOUNT_ALREADY_WITHDRAWN)
        }
        if (status == UserStatus.SUSPENDED) {
            throw CustomException(ErrorCode.ACCOUNT_ALREADY_SUSPENDED)
        }
        this.status = UserStatus.SUSPENDED
    }

    fun reinstate() {
        if (status != UserStatus.SUSPENDED) {
            throw CustomException(ErrorCode.ACCOUNT_NOT_SUSPENDED)
        }
        this.status = UserStatus.ACTIVE
    }

    fun withdraw() {
        if (status == UserStatus.WITHDRAWN) {
            throw CustomException(ErrorCode.ACCOUNT_ALREADY_WITHDRAWN)
        }
        this.status = UserStatus.WITHDRAWN
    }

    companion object {
        @JvmStatic
        fun of(
            email: String?,
            password: String?,
            name: String,
            role: UserRole,
            provider: UserProvider?,
            providerId: String?,
            createdAt: LocalDateTime,
        ): User = User(email, password, name, role, provider ?: UserProvider.LOCAL, providerId, createdAt)
    }
}
