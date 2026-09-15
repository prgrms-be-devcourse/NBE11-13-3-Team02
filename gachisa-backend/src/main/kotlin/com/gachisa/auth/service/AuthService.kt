package com.gachisa.auth.service

import com.gachisa.auth.client.KakaoOAuthClient
import com.gachisa.auth.client.NaverOAuthClient
import com.gachisa.auth.dto.LoginResult
import com.gachisa.global.exception.CustomException
import com.gachisa.global.security.JwtProperties
import com.gachisa.global.security.JwtTokenProvider
import com.gachisa.user.dto.UserInfo
import com.gachisa.user.entity.UserProvider
import com.gachisa.user.entity.UserRole
import com.gachisa.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthService(
    private val userService: UserService,
    private val refreshTokenService: RefreshTokenService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
    private val kakaoOAuthClient: KakaoOAuthClient,
    private val naverOAuthClient: NaverOAuthClient,
) {

    @Transactional
    fun signUp(email: String, rawPassword: String, name: String, role: UserRole): UserInfo {
        return userService.signUp(email, rawPassword, name, role)
    }

    @Transactional
    fun login(email: String, rawPassword: String): LoginResult {
        val userInfo = userService.authenticate(email, rawPassword)
        return issueTokens(userInfo)
    }

    @Transactional
    fun loginWithKakao(code: String, redirectUri: String): LoginResult {
        val oAuthUserInfo = kakaoOAuthClient.authenticate(code, redirectUri, null)
        val userInfo = userService.findOrCreateOAuthUser(UserProvider.KAKAO, oAuthUserInfo)
        return issueTokens(userInfo)
    }

    @Transactional
    fun loginWithNaver(code: String, redirectUri: String, state: String?): LoginResult {
        val oAuthUserInfo = naverOAuthClient.authenticate(code, redirectUri, state)
        val userInfo = userService.findOrCreateOAuthUser(UserProvider.NAVER, oAuthUserInfo)
        return issueTokens(userInfo)
    }

    @Transactional(noRollbackFor = [CustomException::class])
    fun reissue(rawRefreshToken: String): LoginResult {
        val userInfo = refreshTokenService.rotate(rawRefreshToken)
        return issueTokens(userInfo)
    }

    @Transactional
    fun logout(rawRefreshToken: String) {
        refreshTokenService.logout(rawRefreshToken)
    }

    @Transactional
    fun withdraw(userId: Long, rawPassword: String?) {
        userService.withdraw(userId, rawPassword)
        refreshTokenService.revokeAllByUser(userId)
    }

    private fun issueTokens(user: UserInfo): LoginResult {
        val accessToken = jwtTokenProvider.createAccessToken(user.id, user.name, user.role)
        val rawRefreshToken = refreshTokenService.issue(user)
        val expiresIn = jwtProperties.accessTokenValidity().toSeconds()
        return LoginResult(accessToken, TOKEN_TYPE, expiresIn, rawRefreshToken)
    }

    companion object {
        private const val TOKEN_TYPE = "Bearer"
    }
}
