package com.gachisa.auth.dto;

public record ReissueResponse(
    String accessToken,
    String tokenType,
    Long expiresIn
) {}
