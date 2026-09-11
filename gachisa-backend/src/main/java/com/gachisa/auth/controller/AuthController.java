package com.gachisa.auth.controller;

import com.gachisa.auth.dto.*;
import com.gachisa.auth.service.AuthService;
import com.gachisa.user.dto.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "회원가입/로그인/소셜로그인/토큰 재발급/로그아웃. 로그인 계열 응답의 accessToken을 Authorize에 등록해서 다른 API를 테스트하세요.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "sid";

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "구매자/판매자 계정을 생성합니다. 이메일은 중복될 수 없습니다.")
    @PostMapping("/signup")
    public SignUpResponse signup(@Valid @RequestBody SignUpRequest request) {
        UserInfo userInfo = authService.signUp(request.email(), request.password(), request.name(), request.role());
        return new SignUpResponse(userInfo.id(), userInfo.email(), userInfo.name(), userInfo.role().name());
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인합니다. 액세스 토큰은 응답 바디로, " +
            "리프레시 토큰은 httpOnly 쿠키(sid)로 내려갑니다.")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResult result = authService.login(request.email(), request.password());
        setRefreshCookie(response, result.rawRefreshToken());
        return new LoginResponse(result.accessToken(), result.tokenType(), result.expiresIn());
    }

    @Operation(summary = "카카오 소셜 로그인", description = "카카오 인가 코드로 로그인/회원가입을 처리합니다.")
    @PostMapping("/oauth/kakao")
    public LoginResponse loginWithKakao(@Valid @RequestBody OAuthLoginRequest request, HttpServletResponse response) {
        LoginResult result = authService.loginWithKakao(request.code(), request.redirectUri());
        setRefreshCookie(response, result.rawRefreshToken());
        return new LoginResponse(result.accessToken(), result.tokenType(), result.expiresIn());
    }

    @Operation(summary = "네이버 소셜 로그인", description = "네이버 인가 코드로 로그인/회원가입을 처리합니다.")
    @PostMapping("/oauth/naver")
    public LoginResponse loginWithNaver(@Valid @RequestBody OAuthLoginRequest request, HttpServletResponse response) {
        LoginResult result = authService.loginWithNaver(request.code(), request.redirectUri(), request.state());
        setRefreshCookie(response, result.rawRefreshToken());
        return new LoginResponse(result.accessToken(), result.tokenType(), result.expiresIn());
    }

    @Operation(summary = "액세스 토큰 재발급", description = "sid 쿠키의 리프레시 토큰으로 새 액세스 토큰을 발급받습니다. " +
            "요청 바디는 없고, 브라우저가 쿠키를 자동으로 함께 보내야 합니다.")
    @PostMapping("/reissue")
    public ReissueResponse reissue(@CookieValue(REFRESH_COOKIE_NAME) String rawRefreshToken,
                                   HttpServletResponse response) {
        LoginResult result = authService.reissue(rawRefreshToken);
        setRefreshCookie(response, result.rawRefreshToken());
        return new ReissueResponse(result.accessToken(), result.tokenType(), result.expiresIn());
    }

    private void setRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/auth")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Operation(summary = "로그아웃", description = "현재 세션의 리프레시 토큰을 무효화하고 쿠키를 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public void logout(@CookieValue(REFRESH_COOKIE_NAME) String rawRefreshToken, HttpServletResponse response) {
        authService.logout(rawRefreshToken);
        clearRefreshCookie(response);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(0)   // 즉시 만료시켜서 브라우저에서 쿠키 삭제
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
