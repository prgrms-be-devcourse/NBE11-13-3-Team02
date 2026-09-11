package com.gachisa.user.dto;

import com.gachisa.user.entity.UserRole;
import java.time.LocalDateTime;

public record UserInfo(
    Long id,
    String email,
    String name,
    UserRole role,
    LocalDateTime createdAt
) {}
