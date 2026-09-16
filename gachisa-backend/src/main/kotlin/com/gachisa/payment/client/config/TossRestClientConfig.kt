package com.gachisa.payment.client.config

import com.gachisa.global.exception.CustomException
import com.gachisa.global.exception.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.nio.charset.StandardCharsets
import java.time.Duration

@Configuration
class TossRestClientConfig {
    @Bean
    fun tossRestClient(@Value("\${payment.toss.base-url}") baseUrl: String, @Value("\${payment.toss.secret-key:}") secretKey: String): RestClient =
        configure(RestClient.builder(), baseUrl, secretKey).build()

    fun configure(builder: RestClient.Builder, baseUrl: String, secretKey: String): RestClient.Builder {
        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT)
        requestFactory.setReadTimeout(READ_TIMEOUT)
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).defaultHeaders { headers ->
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + HttpHeaders.encodeBasicAuth(secretKey, "", StandardCharsets.UTF_8))
            headers.contentType = MediaType.APPLICATION_JSON
        }.defaultStatusHandler({ it.isError }) { _, response -> throw mapGatewayError(response.statusCode.value()) }
    }

    private fun mapGatewayError(statusCode: Int): CustomException = when {
        statusCode == 409 -> CustomException(ErrorCode.PAYMENT_GATEWAY_PROCESSING)
        statusCode >= 500 || statusCode == 408 || statusCode == 429 -> CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE)
        else -> CustomException(ErrorCode.PAYMENT_GATEWAY_REJECTED)
    }

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
