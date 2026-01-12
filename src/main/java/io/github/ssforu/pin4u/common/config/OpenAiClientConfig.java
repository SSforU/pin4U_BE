// src/main/java/io/github/ssforu/pin4u/common/config/OpenAiClientConfig.java
package io.github.ssforu.pin4u.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OpenAiClientConfig {
    private static final Logger log = LoggerFactory.getLogger(OpenAiClientConfig.class);

    @Bean
    @Qualifier("openaiWebClient")
    public WebClient openaiWebClient(
            WebClient.Builder builder,
            @Value("${app.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.ai.openai.api-key:}") String apiKey
    ) {
        // 키가 없어도 빈은 생성합니다. 호출 시 401 나도 서비스단에서 폴백하므로 안전.
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[OPENAI] API key is empty. Calls may 401, but service will fallback.");
            return builder.baseUrl(baseUrl).build();
        }
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("User-Agent", "pin4u")
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                        .build())
                .build();
    }
}
