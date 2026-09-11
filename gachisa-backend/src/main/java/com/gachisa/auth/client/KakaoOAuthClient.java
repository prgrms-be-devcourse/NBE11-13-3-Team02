package com.gachisa.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.user.entity.UserProvider;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class KakaoOAuthClient implements SocialOAuthClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final String DEFAULT_NAME = "카카오사용자";

    private final RestClient authClient;
    private final RestClient apiClient;
    private final String clientId;
    private final String clientSecret;

    public KakaoOAuthClient(
            @Value("${oauth.kakao.client-id:}") String clientId,
            @Value("${oauth.kakao.client-secret:}") String clientSecret
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authClient = createRestClientBuilder().baseUrl("https://kauth.kakao.com").build();
        this.apiClient = createRestClientBuilder().baseUrl("https://kapi.kakao.com").build();
    }

    @Override
    public UserProvider getProvider() {
        return UserProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo authenticate(String code, String redirectUri, String state) {
        String accessToken = requestAccessToken(code, redirectUri);
        return requestUserInfo(accessToken);
    }

    private String requestAccessToken(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (StringUtils.hasText(clientSecret)) {
            form.add("client_secret", clientSecret);
        }

        try {
            KakaoTokenResponse response = authClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                log.error("카카오 토큰 응답에 access_token이 없음: response={}", response);
                throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return response.accessToken();
        } catch (RestClientResponseException e) {
            log.error("카카오 토큰 발급 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
        } catch (ResourceAccessException e) {
            log.error("카카오 토큰 발급 요청 실패 (네트워크/타임아웃)", e);
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private OAuthUserInfo requestUserInfo(String accessToken) {
        try {
            KakaoUserResponse response = apiClient.get()
                    .uri("/v2/user/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(KakaoUserResponse.class);

            if (response == null || response.kakaoAccount() == null) {
                log.error("카카오 사용자 정보 응답에 kakao_account가 없음 (동의항목 미승인 가능성): response={}", response);
                throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }

            KakaoAccount account = response.kakaoAccount();
            String nickname = account.profile() != null ? account.profile().nickname() : null;

            if (!StringUtils.hasText(account.email())) {
                log.error("카카오 계정에 이메일이 없음 (이메일 동의항목 미승인 가능성): kakaoId={}", response.id());
                throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }

            return new OAuthUserInfo(
                    String.valueOf(response.id()),
                    account.email(),
                    Boolean.TRUE.equals(account.isEmailVerified()),
                    StringUtils.hasText(nickname) ? nickname : DEFAULT_NAME
            );
        } catch (RestClientResponseException e) {
            log.error("카카오 사용자 정보 조회 실패: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
        } catch (ResourceAccessException e) {
            log.error("카카오 사용자 정보 조회 요청 실패 (네트워크/타임아웃)", e);
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private static RestClient.Builder createRestClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }

    private record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {}

    private record KakaoUserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ) {}

    private record KakaoAccount(
            String email,
            @JsonProperty("is_email_verified") Boolean isEmailVerified,
            KakaoProfile profile
    ) {}

    private record KakaoProfile(String nickname) {}
}
