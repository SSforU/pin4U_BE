package io.github.ssforu.pin4u.features.places.infra;

import io.github.ssforu.pin4u.common.config.AppProperties;
import io.github.ssforu.pin4u.features.places.domain.KakaoPayload;
import io.github.ssforu.pin4u.features.places.domain.KakaoSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

//WebClient로 카카오 호출(Resilience4j는 여기서)
@Component
@RequiredArgsConstructor
public class KakaoSearchAdapterImpl implements KakaoSearchPort {
    private final WebClient kakaoWebClient; // common.config.WebClientConfig
    private final AppProperties props;

    @Override
    public KakaoPayload searchKeyword(String query, String stationCode, int limit) {
        if (!props.kakao().enabled()) {
            return KakaoPayload.empty(); // MOCK 경로
        }
        // WebClient 호출 + Resilience4j 어노테이션은 여기서(필요 시)
        //...
        return KakaoPayload.empty();
    }
}
