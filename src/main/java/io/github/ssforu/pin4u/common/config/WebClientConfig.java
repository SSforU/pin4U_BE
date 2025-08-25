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
            @Value("${app.kakao.enabled:true}") boolean enabled,
            // base-url은 없으면 기본값 사용
            @Value("${app.kakao.api.base-url:https://dapi.kakao.com}") String baseUrl,
            // api.key 우선, 없으면 rest-api-key 사용(둘 다 없으면 빈 문자열)
            @Value("${app.kakao.api.key:}") String apiKeyFromApi,
            @Value("${app.kakao.rest-api-key:}") String apiKeyFromRest
    ) {
        final String apiKey = (apiKeyFromApi != null && !apiKeyFromApi.isBlank())
                ? apiKeyFromApi
                : (apiKeyFromRest != null ? apiKeyFromRest : "");

        if (!enabled) {
            // 기능 비활성: 호출 쪽(KakaoSearchAdapterImpl)이 enabled=false로 자체 차단
            log.info("[KAKAO] disabled by config. base={}", baseUrl);
            return builder.baseUrl(baseUrl).build();
        }

        if (apiKey.isBlank()) {
            // 기능은 켜져 있지만 키가 없음 → 런타임 호출 시 401 날 수 있으나, 앱은 부팅 성공
            log.warn("[KAKAO] enabled=true 이지만 API 키가 없습니다. Authorization 없이 생성합니다. (호출 시 401 가능)");
            return builder.baseUrl(baseUrl).build();
        }

        log.info("[KAKAO] enabled. base={}, key.len={}", baseUrl, apiKey.length());
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "KakaoAK " + apiKey)
                .build();
    }
}
