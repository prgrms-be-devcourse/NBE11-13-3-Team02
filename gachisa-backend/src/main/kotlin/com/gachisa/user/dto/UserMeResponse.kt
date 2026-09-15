package com.gachisa.user.dto

import java.time.LocalDateTime

data class UserMeResponse(
    val id: Long,
    val email: String?,
    val name: String,
    val role: String,
    val createdAt: LocalDateTime,
    val status: String,
) {
    companion object {
        @JvmStatic
        fun from(userInfo: UserInfo): UserMeResponse {
            return UserMeResponse(
                id = userInfo.id,
                email = userInfo.email,
                name = userInfo.name,
                role = userInfo.role.name,
                createdAt = userInfo.createdAt,
                status = userInfo.status.name,
            )
        }
    }
}
