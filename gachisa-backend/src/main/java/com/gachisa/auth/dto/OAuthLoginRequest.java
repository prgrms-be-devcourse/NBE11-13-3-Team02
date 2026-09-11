package com.gachisa.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(
    @Schema(description = "소셜 로그인 제공자(카카오/네이버)로부터 리다이렉트로 전달받은 인가 코드")
    @NotBlank String code,
    @Schema(description = "프론트에서 소셜 로그인 인가 요청 시 사용한 redirect_uri와 동일한 값이어야 함")
    @NotBlank String redirectUri,
    @Schema(description = "CSRF 방지용 state 값. 네이버 로그인에서만 필요하고 카카오는 생략 가능")
    String state
) {}
