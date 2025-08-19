package io.github.ssforu.pin4u.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient kakaoWebClient(
            WebClient.Builder builder,
            @Value("${kakao.api.base-url}") String baseUrl,
            @Value("${kakao.api.key}") String apiKey
    ) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "KakaoAK " + apiKey)
                .build();
    }
}
