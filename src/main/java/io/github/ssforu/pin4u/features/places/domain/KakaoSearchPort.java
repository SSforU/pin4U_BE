package io.github.ssforu.pin4u.features.places.domain;

import org.springframework.lang.Nullable;

// 외부 포트 인터페이스
public interface KakaoSearchPort {
    KakaoPayload searchKeyword(String query, @Nullable String stationCode, int limit);
}
