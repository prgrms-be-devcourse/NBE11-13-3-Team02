package com.gachisa.auth.service;

import com.gachisa.auth.client.KakaoOAuthClient;
import com.gachisa.auth.client.NaverOAuthClient;
import com.gachisa.auth.client.OAuthUserInfo;
import com.gachisa.auth.dto.LoginResult;
import com.gachisa.global.exception.CustomException;
import com.gachisa.global.security.JwtProperties;
import com.gachisa.global.security.JwtTokenProvider;
import com.gachisa.user.dto.UserInfo;
import com.gachisa.user.entity.UserProvider;
import com.gachisa.user.entity.UserRole;
import com.gachisa.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final NaverOAuthClient naverOAuthClient;

    @Transactional
    public UserInfo signUp(String email, String rawPassword, String name, UserRole role) {
        return userService.signUp(email, rawPassword, name, role);
    }

    @Transactional
    public LoginResult login(String email, String rawPassword) {
        UserInfo userInfo = userService.authenticate(email, rawPassword);
        return issueTokens(userInfo);
    }

    @Transactional
    public LoginResult loginWithKakao(String code, String redirectUri) {
        OAuthUserInfo oAuthUserInfo = kakaoOAuthClient.authenticate(code, redirectUri, null);
        UserInfo userInfo = userService.findOrCreateOAuthUser(UserProvider.KAKAO, oAuthUserInfo);
        return issueTokens(userInfo);
    }

    @Transactional
    public LoginResult loginWithNaver(String code, String redirectUri, String state) {
        OAuthUserInfo oAuthUserInfo = naverOAuthClient.authenticate(code, redirectUri, state);
        UserInfo userInfo = userService.findOrCreateOAuthUser(UserProvider.NAVER, oAuthUserInfo);
        return issueTokens(userInfo);
    }

    @Transactional(noRollbackFor = CustomException.class)
    public LoginResult reissue(String rawRefreshToken) {
        UserInfo userInfo = refreshTokenService.rotate(rawRefreshToken);
        return issueTokens(userInfo);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.logout(rawRefreshToken);
    }

    private LoginResult issueTokens(UserInfo user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.id(), user.name(), user.role());
        String rawRefreshToken = refreshTokenService.issue(user);
        long expiresIn = jwtProperties.accessTokenValidity().toSeconds();
        return new LoginResult(accessToken, TOKEN_TYPE, expiresIn, rawRefreshToken);
    }
}
