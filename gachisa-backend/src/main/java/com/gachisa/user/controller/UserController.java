package com.gachisa.user.controller;

import com.gachisa.global.security.CustomUserDetails;
import com.gachisa.user.dto.UserInfo;
import com.gachisa.user.dto.UserMeResponse;
import com.gachisa.user.dto.UserUpdateRequest;
import com.gachisa.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "내 정보 조회/수정. 모두 로그인 필요.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public UserMeResponse getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserInfo userInfo = userService.getById(userDetails.getUserId());
        return UserMeResponse.from(userInfo);
    }

    @Operation(summary = "내 정보 수정", description = "이름/비밀번호를 변경합니다. 값을 보낸 필드만 변경됩니다.")
    @PatchMapping("/me")
    public UserMeResponse updateMe(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    @Valid @RequestBody UserUpdateRequest request) {
        UserInfo userInfo = userService.updateMe(
            userDetails.getUserId(),
            request.name(),
            request.currentPassword(),
            request.newPassword()
        );
        return UserMeResponse.from(userInfo);
    }
}
