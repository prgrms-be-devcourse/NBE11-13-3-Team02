package com.gachisa.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    // 전역 기본 보안 요구사항(OpenAPI.security)은 일부러 설정하지 않는다.
    // springdoc(현재 버전)에서 @SecurityRequirement(name = "")/@Operation(security = {})로
    // 엔드포인트별 전역 설정을 "해제"하는 게 반영되지 않는 걸 확인했기 때문에,
    // 대신 인증이 필요한 각 엔드포인트에 @SecurityRequirement(name = "bearerAuth")를
    // 개별적으로 붙이는 방식(추가는 정상 반영됨)으로 대체했다.
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("같이사 API")
                        .description("""
                                공동구매 이커머스 서비스 API 문서입니다.
                                로그인/재발급 응답으로 받은 accessToken을 우측 상단 Authorize 버튼에 'Bearer {accessToken}' 형식으로 입력하면
                                인증이 필요한 API도 바로 테스트할 수 있습니다.
                                자물쇠 아이콘이 없는 API는 인증 없이 호출 가능합니다.
                                """)
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
