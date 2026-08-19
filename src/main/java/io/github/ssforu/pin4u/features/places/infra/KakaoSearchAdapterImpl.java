package io.github.ssforu.pin4u.features.places.infra;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.ssforu.pin4u.common.exception.ApiErrorCode;
import io.github.ssforu.pin4u.common.exception.ApiException;
import io.github.ssforu.pin4u.features.places.domain.KakaoPayload;
import io.github.ssforu.pin4u.features.places.domain.KakaoSearchPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@ConditionalOnBean(name = "kakaoWebClient")
public class KakaoSearchAdapterImpl implements KakaoSearchPort {

    private final WebClient kakao;
    private final boolean enabled;

    public KakaoSearchAdapterImpl(
            @Qualifier("kakaoWebClient") WebClient kakaoWebClient,
            @Value("${app.kakao.enabled:true}") boolean enabled
    ) {
        this.kakao = kakaoWebClient;
        this.enabled = enabled;
    }

    // Retry가 안쪽에서 재시도하고, 재시도 실패가 CircuitBreaker에 기록된다.
    @Override
    @CircuitBreaker(name = "kakaoSearch", fallbackMethod = "keywordSearchFallback")
    @Retry(name = "kakaoSearch")
    public List<KakaoPayload.Document> keywordSearch(
            BigDecimal lat, BigDecimal lng, String query, int radiusM, int size
    ) {
        if (!enabled) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "kakao disabled", null);
        }
        var resp = kakao.get()
                .uri(u -> u.path("/v2/local/search/keyword.json")
                        .queryParam("query", query)
                        .queryParam("y", lat)   // 위도
                        .queryParam("x", lng)   // 경도
                        .queryParam("radius", radiusM)
                        .queryParam("size", size)
                        .build())
                .retrieve()
                .toEntity(KakaoPayload.SearchResponse.class)
                .block();

        if (resp == null || resp.getBody() == null) {
            throw new ApiException(ApiErrorCode.UPSTREAM_ERROR, "kakao search failed", null);
        }
        return resp.getBody().documents();
    }

    @SuppressWarnings("unused")
    private List<KakaoPayload.Document> keywordSearchFallback(
            BigDecimal lat, BigDecimal lng, String query, int radiusM, int size, Throwable t) {
        log.warn("[Kakao] search fallback for query='{}': {}", query, t.getMessage());
        return List.of();
    }
}
