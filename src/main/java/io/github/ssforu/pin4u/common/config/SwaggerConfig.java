// src/main/java/io/github/ssforu/pin4u/common/config/SwaggerConfig.java
package io.github.ssforu.pin4u.common.config;

import io.swagger.v3.oas.models.Components;               // ✅ 올바른 import
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    // 문서상에서만 표시할 쿠키 기반 인증 스키마 이름 (실제 인증은 우리가 세팅한 uid 쿠키로 동작)
    private static final String UID_COOKIE_SCHEME = "uidCookie";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("pin4U API")
                        .version("v1")
                        .description("pin4U 백엔드 API 문서"))
                // ✅ 쿠키(uid) 사용을 문서에 명시 (보안 요구는 문서 레벨)
                .components(new Components()
                        .addSecuritySchemes(UID_COOKIE_SCHEME,
                                new SecurityScheme()
                                        .name("uid")                           // 쿠키 이름
                                        .type(SecurityScheme.Type.APIKEY)      // APIKEY + COOKIE 로 표기
                                        .in(SecurityScheme.In.COOKIE)
                                        .description("로그인 성공 시 발급되는 uid 쿠키")))
                .addSecurityItem(new SecurityRequirement().addList(UID_COOKIE_SCHEME));
    }
}
