package io.github.ssforu.pin4u.features.auth.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    private final WebClient kakaoOAuthWebClient;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoProfile(String nickname) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(KakaoProfile profile) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoMe(long id, KakaoAccount kakao_account) {}

    public Mono<KakaoMe> getMe(String accessToken) {
        return kakaoOAuthWebClient.get()
                .uri("/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(s -> s.value() == 401,
                        resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new IllegalArgumentException("invalid_kakao_token")))
                .onStatus(s -> s.is4xxClientError(),
                        resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new IllegalArgumentException("kakao_4xx")))
                .onStatus(s -> s.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new IllegalStateException("kakao_5xx")))
                .bodyToMono(KakaoMe.class);
    }
}
