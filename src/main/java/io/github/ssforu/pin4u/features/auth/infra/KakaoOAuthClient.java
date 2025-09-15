// src/main/java/io/github/ssforu/pin4u/features/auth/infra/KakaoOAuthClient.java
package io.github.ssforu.pin4u.features.auth.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class KakaoOAuthClient {
    private final WebClient http = WebClient.builder()
            .baseUrl("https://kapi.kakao.com")       // ★ 사용자 API는 kapi
            .build();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoProfile(String nickname) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(KakaoProfile profile) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoMe(long id, KakaoAccount kakao_account) {}

    public Mono<KakaoMe> getMe(String accessToken) {
        return http.get()
                .uri("/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(KakaoMe.class);
    }
}
