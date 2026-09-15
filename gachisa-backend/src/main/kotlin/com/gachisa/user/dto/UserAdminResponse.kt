package com.gachisa.user.dto

import com.gachisa.user.entity.User
import com.gachisa.user.entity.UserProvider
import com.gachisa.user.entity.UserRole
import com.gachisa.user.entity.UserStatus
import java.time.LocalDateTime

data class UserAdminResponse(
    val id: Long,
    val email: String?,
    val name: String,
    val role: UserRole,
    val provider: UserProvider,
    val status: UserStatus,
    val createdAt: LocalDateTime,
) {
    companion object {
        @JvmStatic
        fun from(user: User): UserAdminResponse {
            return UserAdminResponse(
                id = user.id!!,
                email = user.email,
                name = user.name,
                role = user.role,
                provider = user.provider,
                status = user.status,
                createdAt = user.createdAt,
            )
        }
    }
}
