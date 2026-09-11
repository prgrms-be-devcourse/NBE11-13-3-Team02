package com.gachisa.auth.dto;

public record LoginResponse(
    String accessToken,
    String tokenType,
    Long expiresIn
) {}

