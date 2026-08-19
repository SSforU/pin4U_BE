package io.github.ssforu.pin4u.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OpenAiClientConfig {
    private static final Logger log = LoggerFactory.getLogger(OpenAiClientConfig.class);

    @Bean
    @Qualifier("openaiWebClient")
    public WebClient openaiWebClient(
            WebClient.Builder builder,
            @Value("${app.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.ai.openai.api-key:}") String apiKey,
            @Value("${app.http.openai.connect-timeout:2s}") Duration connectTimeout,
            @Value("${app.http.openai.response-timeout:20s}") Duration responseTimeout
    ) {
        // 비동기 백그라운드 호출이므로 response timeout을 여유 있게 설정 (20s).
        // GPT 응답은 모델/프롬프트에 따라 10s+ 소요될 수 있다.
        HttpClient httpClient = WebClientConfig.buildHttpClient(connectTimeout, responseTimeout);

        var b = builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                        .build());

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[OPENAI] API key is empty. Calls may 401, but service will fallback.");
            return b.build();
        }
        return b
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("User-Agent", "pin4u")
                .build();
    }
}
