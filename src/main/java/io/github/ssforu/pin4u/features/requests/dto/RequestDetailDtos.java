package io.github.ssforu.pin4u.features.requests.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class RequestDetailDtos {

    private RequestDetailDtos() {}

    public record Station(
            String code,
            String name,
            String line,
            BigDecimal lat,
            BigDecimal lng
    ) {}

    public record Ai(
            String summaryText,
            String evidence,
            OffsetDateTime updatedAt
    ) {}

    public record Mock(
            Double rating,
            Integer ratingCount,
            List<String> imageUrls,
            List<String> openingHours
    ) {}

    public record Item(
            String externalId,
            String id,
            String placeName,
            String categoryGroupCode,
            String categoryGroupName,
            String categoryName,
            String addressName,
            String roadAddressName,
            String x,
            String y,
            Integer distanceM,
            String placeUrl,
            @JsonInclude(JsonInclude.Include.NON_NULL) Mock mock,
            @JsonInclude(JsonInclude.Include.NON_NULL) Ai ai,
            @JsonProperty("recommended_count") Integer recommendedCount
    ) {}

    // ★ 그룹 요약(그룹지도 응답에서만 채움; 개인지도는 null)
    public record GroupBrief(
            Long id,
            String slug,
            String name,
            @JsonProperty("image_url") String imageUrl
    ) {}

    // ★ 개인지도 스키마 유지 + group(옵션) 추가
    public record RequestDetailResponse(
            String slug,
            Station station,
            String requestMessage,
            List<Item> items,
            @JsonInclude(JsonInclude.Include.NON_NULL) GroupBrief group
    ) {}
}
