package com.gachisa.auth.dto;

public record LoginResult(
    String accessToken,
    String tokenType,
    Long expiresIn,
    String rawRefreshToken
) {}
