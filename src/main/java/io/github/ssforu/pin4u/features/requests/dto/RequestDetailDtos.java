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
            Object evidence,      // JSONB → Map/JsonNode (전역 ObjectMapper 설정에 따름)
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
            String x,  // Kakao parity: 문자열
            String y,  // Kakao parity: 문자열
            Integer distanceM,
            String placeUrl,
            @JsonInclude(JsonInclude.Include.NON_NULL) Mock mock,
            @JsonInclude(JsonInclude.Include.NON_NULL) Ai ai,
            @JsonProperty("recommended_count") Integer recommendedCount // ★ 명시적으로 recommended_count로 직렬화
    ) {}

    public record RequestDetailResponse(
            String slug,
            Station station,
            String requestMessage,
            List<Item> items
    ) {}
}
