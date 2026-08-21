package io.github.ssforu.pin4u.common.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Bean
    public WebClient kakaoWebClient(
            WebClient.Builder builder,
            @Value("${app.kakao.enabled:true}") boolean enabled,
            @Value("${app.kakao.api.base-url:https://dapi.kakao.com}") String baseUrl,
            @Value("${app.kakao.api.key:}") String apiKeyFromApi,
            @Value("${app.kakao.rest-api-key:}") String apiKeyFromRest,
            @Value("${app.http.kakao-search.connect-timeout:2s}") Duration connectTimeout,
            @Value("${app.http.kakao-search.response-timeout:3s}") Duration responseTimeout
    ) {
        final String apiKey = (apiKeyFromApi != null && !apiKeyFromApi.isBlank())
                ? apiKeyFromApi
                : (apiKeyFromRest != null ? apiKeyFromRest : "");

        // 사용자 응답 경로이므로 타임아웃을 짧게 설정 (connect 2s, response 3s)
        HttpClient httpClient = buildHttpClient(connectTimeout, responseTimeout);
        var b = builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl);

        if (!enabled) {
            log.info("[KAKAO] disabled by config. base={}", baseUrl);
            return b.build();
        }
        if (apiKey.isBlank()) {
            log.warn("[KAKAO] enabled=true but API key is empty.");
            return b.build();
        }

        log.info("[KAKAO] enabled. base={}, key.len={}", baseUrl, apiKey.length());
        return b.defaultHeader("Authorization", "KakaoAK " + apiKey).build();
    }

    @Bean
    public WebClient kakaoOAuthWebClient(
            WebClient.Builder builder,
            @Value("${app.http.kakao-oauth.connect-timeout:2s}") Duration connectTimeout,
            @Value("${app.http.kakao-oauth.response-timeout:3s}") Duration responseTimeout
    ) {
        // 로그인 경로. 실패 시 사용자에게 즉시 재시도를 유도해야 하므로 짧은 타임아웃.
        HttpClient httpClient = buildHttpClient(connectTimeout, responseTimeout);
        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://kapi.kakao.com")
                .build();
    }

    static HttpClient buildHttpClient(Duration connectTimeout, Duration responseTimeout) {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .responseTimeout(responseTimeout)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(responseTimeout.toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(responseTimeout.toSeconds(), TimeUnit.SECONDS)));
    }
}
