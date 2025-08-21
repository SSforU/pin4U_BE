package io.github.ssforu.pin4u.features.places.domain;

import java.util.List;

public final class KakaoPayload {
    public record SearchResponse(List<Document> documents) {}

    public record Document(
            String id,
            String place_name,
            String category_group_code,
            String category_group_name,
            String category_name,
            String phone,
            String address_name,
            String road_address_name,
            String x,  // 문자열
            String y,  // 문자열
            String place_url,
            String distance // 미터 문자열(없을 수도 있음)
    ) {}
}
