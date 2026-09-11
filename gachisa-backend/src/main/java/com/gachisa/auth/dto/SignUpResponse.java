package com.gachisa.auth.dto;

public record SignUpResponse(
    Long id,
    String email,
    String name,
    String role
) {}
