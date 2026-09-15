package com.gachisa.auth.dto

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.user.entity.UserRole
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:Schema(description = "가입 시 사용한 이메일", example = "buyer1@test.com")
    @field:NotBlank
    val email: String,
    @field:Schema(description = "비밀번호", example = "1234")
    @field:NotBlank
    val password: String,
)

data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)

data class LoginResult(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val rawRefreshToken: String,
)

data class OAuthLoginRequest(
    @field:Schema(description = "소셜 로그인 제공자(카카오/네이버)로부터 리다이렉트로 전달받은 인가 코드")
    @field:NotBlank
    val code: String,
    @field:Schema(description = "프론트에서 소셜 로그인 인가 요청 시 사용한 redirect_uri와 동일한 값이어야 함")
    @field:NotBlank
    val redirectUri: String,
    @field:Schema(description = "CSRF 방지용 state 값. 네이버 로그인에서만 필요하고 카카오는 생략 가능")
    val state: String?,
)

data class ReissueResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)

data class SignUpRequest(
    @field:Schema(description = "이메일 (로그인 아이디로 사용)", example = "buyer1@test.com")
    @field:NotBlank
    @field:Email
    val email: String,
    @field:Schema(description = "비밀번호 (4자 이상)", example = "1234")
    @field:NotBlank
    @field:Size(min = 4)
    val password: String,
    @field:Schema(description = "이름/닉네임", example = "홍길동")
    @field:NotBlank
    val name: String,
    @field:Schema(
        description = "가입 권한. 구매자 또는 판매자만 선택 가능 (관리자는 회원가입으로 생성 불가)",
        example = "ROLE_BUYER",
    )
    @field:NotNull
    val role: UserRole?,
) {
    init {
        if (role != UserRole.ROLE_BUYER && role != UserRole.ROLE_SELLER) {
            throw CustomException(ErrorCode.INVALID_SIGNUP_ROLE)
        }
    }
}

data class SignUpResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: String,
)

@Schema(description = "회원 탈퇴 요청")
data class WithdrawRequest(
    @field:Schema(description = "현재 비밀번호. 소셜 전용 계정(비밀번호 없음)은 생략 가능", example = "1234")
    val password: String? = null,
)
