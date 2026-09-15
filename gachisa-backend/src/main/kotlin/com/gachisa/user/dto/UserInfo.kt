package com.gachisa.user.dto

import com.gachisa.user.entity.UserRole
import com.gachisa.user.entity.UserStatus
import java.time.LocalDateTime

data class UserInfo(
    val id: Long,
    val email: String?,
    val name: String,
    val role: UserRole,
    val createdAt: LocalDateTime,
    val status: UserStatus,
)
