package com.gachisa.auth.client;

public record OAuthUserInfo(
    String providerId,
    String email,
    boolean emailVerified,
    String name
) {}
