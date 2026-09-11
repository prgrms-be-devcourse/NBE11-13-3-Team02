package com.gachisa.auth.dto;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.user.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
    @Schema(description = "이메일 (로그인 아이디로 사용)", example = "buyer1@test.com")
    @NotBlank @Email String email,
    @Schema(description = "비밀번호 (4자 이상)", example = "1234")
    @NotBlank @Size(min = 4) String password,
    @Schema(description = "이름/닉네임", example = "홍길동")
    @NotBlank String name,
    @Schema(description = "가입 권한. 구매자 또는 판매자만 선택 가능 (관리자는 회원가입으로 생성 불가)",
            example = "ROLE_BUYER")
    @NotNull UserRole role
) {
    public SignUpRequest {
        if (role != UserRole.ROLE_BUYER && role != UserRole.ROLE_SELLER) {
            throw new CustomException(ErrorCode.INVALID_SIGNUP_ROLE);
        }
    }
}
