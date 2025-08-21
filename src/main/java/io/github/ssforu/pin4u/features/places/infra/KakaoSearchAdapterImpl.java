package io.github.ssforu.pin4u.features.places.infra;

import io.github.ssforu.pin4u.common.exception.ApiErrorCode;
import io.github.ssforu.pin4u.common.exception.ApiException;
import io.github.ssforu.pin4u.features.places.domain.KakaoPayload;
import io.github.ssforu.pin4u.features.places.domain.KakaoSearchPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;

@Component
public class KakaoSearchAdapterImpl implements KakaoSearchPort {

    private final WebClient kakao;
    private final boolean enabled;

    public KakaoSearchAdapterImpl(
            WebClient kakaoWebClient,
            @Value("${app.kakao.enabled:true}") boolean enabled // ★ 변경
    ) {
        this.kakao = kakaoWebClient;
        this.enabled = enabled;
    }

    @Override
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
}
