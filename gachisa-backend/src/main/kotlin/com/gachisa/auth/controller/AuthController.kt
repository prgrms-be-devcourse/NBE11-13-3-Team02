package com.gachisa.auth.controller

import com.gachisa.auth.dto.LoginRequest
import com.gachisa.auth.dto.LoginResponse
import com.gachisa.auth.dto.OAuthLoginRequest
import com.gachisa.auth.dto.ReissueResponse
import com.gachisa.auth.dto.SignUpRequest
import com.gachisa.auth.dto.SignUpResponse
import com.gachisa.auth.dto.WithdrawRequest
import com.gachisa.auth.service.AuthService
import com.gachisa.global.security.CustomUserDetails
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "Auth",
    description = "회원가입/로그인/소셜로그인/토큰 재발급/로그아웃. 로그인 계열 응답의 accessToken을 Authorize에 등록해서 다른 API를 테스트하세요.",
)
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {

    @Operation(summary = "회원가입", description = "구매자/판매자 계정을 생성합니다. 이메일은 중복될 수 없습니다.")
    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignUpRequest): SignUpResponse {
        val userInfo = authService.signUp(request.email, request.password, request.name, request.role!!)
        return SignUpResponse(userInfo.id, userInfo.email ?: "", userInfo.name, userInfo.role.name)
    }

    @Operation(
        summary = "로그인",
        description = "이메일/비밀번호로 로그인합니다. 액세스 토큰은 응답 바디로, " +
            "리프레시 토큰은 httpOnly 쿠키(sid)로 내려갑니다.",
    )
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, response: HttpServletResponse): LoginResponse {
        val result = authService.login(request.email, request.password)
        setRefreshCookie(response, result.rawRefreshToken)
        return LoginResponse(result.accessToken, result.tokenType, result.expiresIn)
    }

    @Operation(summary = "카카오 소셜 로그인", description = "카카오 인가 코드로 로그인/회원가입을 처리합니다.")
    @PostMapping("/oauth/kakao")
    fun loginWithKakao(@Valid @RequestBody request: OAuthLoginRequest, response: HttpServletResponse): LoginResponse {
        val result = authService.loginWithKakao(request.code, request.redirectUri)
        setRefreshCookie(response, result.rawRefreshToken)
        return LoginResponse(result.accessToken, result.tokenType, result.expiresIn)
    }

    @Operation(summary = "네이버 소셜 로그인", description = "네이버 인가 코드로 로그인/회원가입을 처리합니다.")
    @PostMapping("/oauth/naver")
    fun loginWithNaver(@Valid @RequestBody request: OAuthLoginRequest, response: HttpServletResponse): LoginResponse {
        val result = authService.loginWithNaver(request.code, request.redirectUri, request.state)
        setRefreshCookie(response, result.rawRefreshToken)
        return LoginResponse(result.accessToken, result.tokenType, result.expiresIn)
    }

    @Operation(
        summary = "액세스 토큰 재발급",
        description = "sid 쿠키의 리프레시 토큰으로 새 액세스 토큰을 발급받습니다. " +
            "요청 바디는 없고, 브라우저가 쿠키를 자동으로 함께 보내야 합니다.",
    )
    @PostMapping("/reissue")
    fun reissue(
        @CookieValue(REFRESH_COOKIE_NAME) rawRefreshToken: String,
        response: HttpServletResponse,
    ): ReissueResponse {
        val result = authService.reissue(rawRefreshToken)
        setRefreshCookie(response, result.rawRefreshToken)
        return ReissueResponse(result.accessToken, result.tokenType, result.expiresIn)
    }

    private fun setRefreshCookie(response: HttpServletResponse, rawRefreshToken: String) {
        val cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/auth")
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    @Operation(summary = "로그아웃", description = "현재 세션의 리프레시 토큰을 무효화하고 쿠키를 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    fun logout(@CookieValue(REFRESH_COOKIE_NAME) rawRefreshToken: String, response: HttpServletResponse) {
        authService.logout(rawRefreshToken)
        clearRefreshCookie(response)
    }

    @Operation(
        summary = "회원 탈퇴",
        description = "본인 계정을 탈퇴 처리합니다. 비밀번호가 설정된 계정은 확인이 필요하고, 소셜 전용 계정은 생략할 수 있습니다. " +
            "이메일은 즉시 익명화되어 다른 사람이 재사용할 수 있게 되지만, 같은 이메일로는 24시간 동안 재가입할 수 없습니다. " +
            "리프레시 토큰은 즉시 전량 폐기됩니다. 되돌릴 수 없습니다.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/withdraw")
    fun withdraw(
        @AuthenticationPrincipal userDetails: CustomUserDetails,
        @RequestBody(required = false) request: WithdrawRequest?,
        response: HttpServletResponse,
    ) {
        authService.withdraw(userDetails.userId, request?.password)
        clearRefreshCookie(response)
    }

    private fun clearRefreshCookie(response: HttpServletResponse) {
        val cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(0) // 즉시 만료시켜서 브라우저에서 쿠키 삭제
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

    companion object {
        private const val REFRESH_COOKIE_NAME = "sid"
    }
}
