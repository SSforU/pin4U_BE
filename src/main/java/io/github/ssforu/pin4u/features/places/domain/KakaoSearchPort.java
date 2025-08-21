package io.github.ssforu.pin4u.features.places.domain;

import java.math.BigDecimal;
import java.util.List;

public interface KakaoSearchPort {
    /** 키워드 + 반경 검색 (lat=y, lng=x, 카카오 스펙) */
    List<KakaoPayload.Document> keywordSearch(
            BigDecimal lat, BigDecimal lng, String query, int radiusM, int size
    );
}
