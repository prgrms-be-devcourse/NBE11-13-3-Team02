package com.gachisa.payment.client.config;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TossRestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient tossRestClient(
            @Value("${payment.toss.base-url}") String baseUrl,
            @Value("${payment.toss.secret-key:}") String secretKey
    ) {
        return configure(RestClient.builder(), baseUrl, secretKey).build();
    }

    public RestClient.Builder configure(
            RestClient.Builder builder,
            String baseUrl,
            String secretKey
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    headers.set(
                            HttpHeaders.AUTHORIZATION,
                            "Basic " + HttpHeaders.encodeBasicAuth(secretKey, "", StandardCharsets.UTF_8)
                    );
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .defaultStatusHandler(
                        status -> status.isError(),
                        (request, response) -> {
                            throw mapGatewayError(response.getStatusCode().value());
                        }
                );
    }

    private CustomException mapGatewayError(int statusCode) {
        if (statusCode == 409) {
            return new CustomException(ErrorCode.PAYMENT_GATEWAY_PROCESSING);
        }
        if (statusCode >= 500 || statusCode == 408 || statusCode == 429) {
            return new CustomException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
        }
        return new CustomException(ErrorCode.PAYMENT_GATEWAY_REJECTED);
    }
}
