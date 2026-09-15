package com.gachisa.auth.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "회원 탈퇴 요청")
data class WithdrawRequest(
    @field:Schema(description = "현재 비밀번호. 소셜 전용 계정(비밀번호 없음)은 생략 가능", example = "1234")
    val password: String? = null,
)
