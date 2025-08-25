package io.github.ssforu.pin4u.features.requests.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;
import java.util.List;

public final class RequestDtos {
    private RequestDtos() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateRequest(
            String ownerNickname,
            String stationCode,
            String requestMessage
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreatedRequestDTO(
            String slug,
            String ownerNickname,
            String stationCode,
            String requestMessage,
            OffsetDateTime createdAt
    ) {}

    // ✅ 리스트 아이템: 스펙 확정 버전 (snake_case)
    //    - station_code 제외
    //    - station_line 다음에 road_address_name 포함(현재 null)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ListItem(
            String slug,
            String stationName,      // “숭실대입구”
            String stationLine,      // “7호선”
            String roadAddressName,  // 현재는 null(또는 생략)
            int    recommendCount,
            OffsetDateTime createdAt
    ) {}

    public record ListResponse(List<ListItem> items, int count) {}
}
