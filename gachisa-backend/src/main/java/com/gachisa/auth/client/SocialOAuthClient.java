package com.gachisa.auth.client;

import com.gachisa.user.entity.UserProvider;

public interface SocialOAuthClient {

    UserProvider getProvider();

    /**
     * 인가 code를 access token으로 교환한 뒤, 프로필 조회까지 마친 결과를 반환한다.
     * state는 네이버만 필요로 하며(CSRF 방지용), 카카오 구현체는 무시한다.
     */
    OAuthUserInfo authenticate(String code, String redirectUri, String state);
}
