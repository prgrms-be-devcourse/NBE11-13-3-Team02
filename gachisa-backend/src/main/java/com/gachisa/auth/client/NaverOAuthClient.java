package com.gachisa.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.user.entity.UserProvider;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NaverOAuthClient implements SocialOAuthClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final String DEFAULT_NAME = "네이버사용자";
    private static final String SUCCESS_CODE = "00";

    private final RestClient authClient;
    private final RestClient apiClient;
    private final String clientId;
    private final String clientSecret;

    public NaverOAuthClient(
            @Value("${oauth.naver.client-id:}") String clientId,
            @Value("${oauth.naver.client-secret:}") String clientSecret
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authClient = createRestClientBuilder().baseUrl("https://nid.naver.com").build();
        this.apiClient = createRestClientBuilder().baseUrl("https://openapi.naver.com").build();
    }

    @Override
    public UserProvider getProvider() {
        return UserProvider.NAVER;
    }

    @Override
    public OAuthUserInfo authenticate(String code, String redirectUri, String state) {
        String accessToken = requestAccessToken(code, state);
        return requestUserInfo(accessToken);
    }

    private String requestAccessToken(String code, String state) {
        try {
            NaverTokenResponse response = authClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/oauth2.0/token")
                            .queryParam("grant_type", "authorization_code")
                            .queryParam("client_id", clientId)
                            .queryParam("client_secret", clientSecret)
                            .queryParam("code", code)
                            .queryParam("state", state)
                            .build())
                    .retrieve()
                    .body(NaverTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return response.accessToken();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private OAuthUserInfo requestUserInfo(String accessToken) {
        try {
            NaverUserResponse response = apiClient.get()
                    .uri("/v1/nid/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(NaverUserResponse.class);

            if (response == null || !SUCCESS_CODE.equals(response.resultCode()) || response.response() == null) {
                throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
            }

            NaverProfile profile = response.response();
            // 네이버는 이메일 제공 자체가 자체 인증을 거친 값만 내려주므로, email이 존재하면 인증된 것으로 간주한다.
            return new OAuthUserInfo(
                    profile.id(),
                    profile.email(),
                    StringUtils.hasText(profile.email()),
                    StringUtils.hasText(profile.name()) ? profile.name() : DEFAULT_NAME
            );
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
    }

    private static RestClient.Builder createRestClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }

    private record NaverTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {}

    private record NaverUserResponse(
            @JsonProperty("resultcode") String resultCode,
            NaverProfile response
    ) {}

    private record NaverProfile(
            String id,
            String email,
            String name
    ) {}
}
