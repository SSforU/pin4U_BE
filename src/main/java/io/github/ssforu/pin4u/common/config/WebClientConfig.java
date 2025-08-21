package io.github.ssforu.pin4u.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Bean
    public WebClient kakaoWebClient(
            WebClient.Builder builder,
            @Value("${app.kakao.api.base-url}") String baseUrl,
            @Value("${app.kakao.api.key}") String apiKey
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "KAKAO_REST_API_KEY 환경변수가 설정되지 않았습니다. " +
                            "IntelliJ Run/Debug 설정 또는 셸에서 환경변수를 주입해 주세요.");
        }
        log.info("[KAKAO] base={}, key.len={}", baseUrl, apiKey.length());
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "KakaoAK " + apiKey)
                .build();
    }
}
