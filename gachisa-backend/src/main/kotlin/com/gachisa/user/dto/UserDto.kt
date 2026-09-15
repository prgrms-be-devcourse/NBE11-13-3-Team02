package com.gachisa.user.dto

import com.gachisa.user.entity.User
import com.gachisa.user.entity.UserProvider
import com.gachisa.user.entity.UserRole
import com.gachisa.user.entity.UserStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
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

data class UserInfo(
    val id: Long,
    val email: String?,
    val name: String,
    val role: UserRole,
    val createdAt: LocalDateTime,
    val status: UserStatus,
)

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

// 부분 수정 DTO: 필드가 null이면 "변경하지 않음"을 의미하므로 @NotBlank는 붙이지 않는다
// (UserService.updateMe의 null/blank 체크와 의미가 맞아야 함)
@Schema(description = "내 정보 수정 요청. 값을 보낸 필드만 변경되고, 생략(null)한 필드는 그대로 유지됨")
data class UserUpdateRequest(
    @field:Schema(description = "변경할 이름. 변경하지 않으려면 생략", example = "홍길동")
    val name: String?,
    @field:Schema(description = "현재 비밀번호. newPassword를 보낼 때만 필요(소셜 전용 계정이 최초 비밀번호를 설정하는 경우는 제외)")
    val currentPassword: String?,
    @field:Schema(description = "새 비밀번호(4자 이상). 변경하지 않으려면 생략")
    @field:Size(min = 4)
    val newPassword: String?,
)
