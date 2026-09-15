package com.gachisa.auth.client

import com.fasterxml.jackson.annotation.JsonProperty
import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import com.gachisa.user.entity.UserProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.util.StringUtils
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.Duration

@Component
class KakaoOAuthClient(
    @Value("\${oauth.kakao.client-id:}") private val clientId: String,
    @Value("\${oauth.kakao.client-secret:}") private val clientSecret: String,
) : SocialOAuthClient {

    private val authClient: RestClient = createRestClientBuilder().baseUrl("https://kauth.kakao.com").build()
    private val apiClient: RestClient = createRestClientBuilder().baseUrl("https://kapi.kakao.com").build()

    override fun getProvider(): UserProvider = UserProvider.KAKAO

    override fun authenticate(code: String, redirectUri: String, state: String?): OAuthUserInfo {
        val accessToken = requestAccessToken(code, redirectUri)
        return requestUserInfo(accessToken)
    }

    private fun requestAccessToken(code: String, redirectUri: String): String {
        val form: MultiValueMap<String, String> = LinkedMultiValueMap()
        form.add("grant_type", "authorization_code")
        form.add("client_id", clientId)
        form.add("redirect_uri", redirectUri)
        form.add("code", code)
        if (StringUtils.hasText(clientSecret)) {
            form.add("client_secret", clientSecret)
        }

        try {
            val response = authClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse::class.java)

            if (response?.accessToken == null) {
                log.error("카카오 토큰 응답에 access_token이 없음: response={}", response)
                throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
            }
            return response.accessToken
        } catch (e: RestClientResponseException) {
            log.error("카카오 토큰 발급 실패: status={}, body={}", e.statusCode, e.responseBodyAsString)
            throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
        } catch (e: ResourceAccessException) {
            log.error("카카오 토큰 발급 요청 실패 (네트워크/타임아웃)", e)
            throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
        }
    }

    private fun requestUserInfo(accessToken: String): OAuthUserInfo {
        try {
            val response = apiClient.get()
                .uri("/v2/user/me")
                .headers { headers -> headers.setBearerAuth(accessToken) }
                .retrieve()
                .body(KakaoUserResponse::class.java)

            if (response?.kakaoAccount == null) {
                log.error("카카오 사용자 정보 응답에 kakao_account가 없음 (동의항목 미승인 가능성): response={}", response)
                throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
            }

            val account = response.kakaoAccount
            val nickname = account.profile?.nickname

            if (!StringUtils.hasText(account.email)) {
                log.error("카카오 계정에 이메일이 없음 (이메일 동의항목 미승인 가능성): kakaoId={}", response.id)
                throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
            }

            return OAuthUserInfo(
                providerId = response.id.toString(),
                email = account.email!!,
                emailVerified = account.isEmailVerified == true,
                name = if (StringUtils.hasText(nickname)) nickname!! else DEFAULT_NAME,
            )
        } catch (e: RestClientResponseException) {
            log.error("카카오 사용자 정보 조회 실패: status={}, body={}", e.statusCode, e.responseBodyAsString)
            throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
        } catch (e: ResourceAccessException) {
            log.error("카카오 사용자 정보 조회 요청 실패 (네트워크/타임아웃)", e)
            throw CustomException(ErrorCode.OAUTH_PROVIDER_ERROR)
        }
    }

    private data class KakaoTokenResponse(
        @param:JsonProperty("access_token") val accessToken: String?,
    )

    private data class KakaoUserResponse(
        val id: Long?,
        @param:JsonProperty("kakao_account") val kakaoAccount: KakaoAccount?,
    )

    private data class KakaoAccount(
        val email: String?,
        @param:JsonProperty("is_email_verified") val isEmailVerified: Boolean?,
        val profile: KakaoProfile?,
    )

    private data class KakaoProfile(val nickname: String?)

    companion object {
        private val log = LoggerFactory.getLogger(KakaoOAuthClient::class.java)
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
        private const val DEFAULT_NAME = "카카오사용자"

        private fun createRestClientBuilder(): RestClient.Builder {
            val requestFactory = SimpleClientHttpRequestFactory()
            requestFactory.setConnectTimeout(CONNECT_TIMEOUT)
            requestFactory.setReadTimeout(READ_TIMEOUT)
            return RestClient.builder().requestFactory(requestFactory)
        }
    }
}
