package com.gachisa.auth.dto

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.user.entity.UserRole
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

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
