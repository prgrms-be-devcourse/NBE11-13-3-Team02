package com.gachisa.auth.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.user.entity.UserProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.Duration

@Component
class NaverOAuthClient(
    @Value("\${oauth.naver.client-id:}") private val clientId: String,
    @Value("\${oauth.naver.client-secret:}") private val clientSecret: String,
) : SocialOAuthClient {

    private val authClient: RestClient = createRestClientBuilder().baseUrl("https://nid.naver.com").build()
    private val apiClient: RestClient = createRestClientBuilder().baseUrl("https://openapi.naver.com").build()

    override fun getProvider(): UserProvider = UserProvider.NAVER

    override fun authenticate(code: String, redirectUri: String, state: String?): OAuthUserInfo {
        val accessToken = requestAccessToken(code, state)
        return requestUserInfo(accessToken)
    }

    private fun requestAccessToken(code: String, state: String?): String {
        try {
            val response = authClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/oauth2.0/token")
                        .queryParam("grant_type", "authorization_code")
                        .queryParam("client_id", clientId)
                        .queryParam("client_secret", clientSecret)
                        .queryParam("code", code)
                        .queryParam("state", state ?: "")
                        .build()
                }
                .retrieve()
                .body(NaverTokenResponse::class.java)

            if (response?.accessToken == null) {
                throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
            }
            return response.accessToken
        } catch (e: RestClientResponseException) {
            log.error("네이버 토큰 발급 실패: status={}, body={}", e.statusCode, e.responseBodyAsString)
            throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
        } catch (e: ResourceAccessException) {
            log.error("네이버 토큰 발급 요청 실패 (네트워크/타임아웃)", e)
            throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
        }
    }

    private fun requestUserInfo(accessToken: String): OAuthUserInfo {
        try {
            val response = apiClient.get()
                .uri("/v1/nid/me")
                .headers { headers -> headers.setBearerAuth(accessToken) }
                .retrieve()
                .body(NaverUserResponse::class.java)

            if (response == null || SUCCESS_CODE != response.resultCode || response.response == null) {
                throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
            }

            val profile = response.response
            // 네이버는 이메일 제공 자체가 자체 인증을 거친 값만 내려주므로, email이 존재하면 인증된 것으로 간주한다.
            return OAuthUserInfo(
                providerId = profile.id ?: "",
                email = profile.email ?: "",
                emailVerified = StringUtils.hasText(profile.email),
                name = if (StringUtils.hasText(profile.name)) profile.name!! else DEFAULT_NAME,
            )
        } catch (e: RestClientResponseException) {
            log.error("네이버 사용자 정보 조회 실패: status={}, body={}", e.statusCode, e.responseBodyAsString)
            throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
        } catch (e: ResourceAccessException) {
            log.error("네이버 사용자 정보 조회 요청 실패 (네트워크/타임아웃)", e)
            throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
        }
    }

    private data class NaverTokenResponse(
        @param:JsonProperty("access_token") val accessToken: String?,
    )

    private data class NaverUserResponse(
        @param:JsonProperty("resultcode") val resultCode: String?,
        val response: NaverProfile?,
    )

    private data class NaverProfile(
        val id: String?,
        val email: String?,
        val name: String?,
    )

    companion object {
        private val log = LoggerFactory.getLogger(NaverOAuthClient::class.java)
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
        private const val DEFAULT_NAME = "네이버사용자"
        private const val SUCCESS_CODE = "00"

        private fun createRestClientBuilder(): RestClient.Builder {
            val requestFactory = SimpleClientHttpRequestFactory()
            requestFactory.setConnectTimeout(CONNECT_TIMEOUT)
            requestFactory.setReadTimeout(READ_TIMEOUT)
            return RestClient.builder().requestFactory(requestFactory)
        }
    }
}
