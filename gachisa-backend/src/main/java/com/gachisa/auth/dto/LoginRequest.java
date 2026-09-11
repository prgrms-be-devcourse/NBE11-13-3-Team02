package com.gachisa.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Schema(description = "가입 시 사용한 이메일", example = "buyer1@test.com")
    @NotBlank String email,
    @Schema(description = "비밀번호", example = "1234")
    @NotBlank String password
) {}
