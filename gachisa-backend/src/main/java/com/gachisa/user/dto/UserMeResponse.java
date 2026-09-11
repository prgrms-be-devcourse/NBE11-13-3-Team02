package com.gachisa.user.dto;

import java.time.LocalDateTime;

public record UserMeResponse(
    Long id,
    String email,
    String name,
    String role,
    LocalDateTime createdAt
) {
    public static UserMeResponse from(UserInfo userInfo) {
        return new UserMeResponse(
            userInfo.id(),
            userInfo.email(),
            userInfo.name(),
            userInfo.role().name(),
            userInfo.createdAt()
        );
    }
}
