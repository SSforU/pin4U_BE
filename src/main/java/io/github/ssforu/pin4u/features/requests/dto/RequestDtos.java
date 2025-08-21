package io.github.ssforu.pin4u.features.requests.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

public final class RequestDtos {
    private RequestDtos() {}

    // 요청 바디: 그대로 사용
    public record CreateRequest(
            @JsonProperty("owner_nickname") String ownerNickname,
            @JsonProperty("station_code") String stationCode,
            @JsonProperty("request_message") String requestMessage
    ) {}

    // 생성 결과: 응답 키 snake_case
    public record CreatedRequestDTO(
            String slug,
            @JsonProperty("owner_nickname") String ownerNickname,
            @JsonProperty("station_code") String stationCode,
            @JsonProperty("request_message") String requestMessage,
            @JsonProperty("created_at") OffsetDateTime createdAt
    ) {}

    // 리스트 아이템: 스펙 확정 버전 (snake_case)
    public record ListItem(
            String slug,
            @JsonProperty("station_name") String stationName,
            @JsonProperty("station_line") String stationLine,
            @JsonProperty("road_address_name") String roadAddressName, // 현재 null 허용
            @JsonProperty("recommend_count") int recommendCount,
            @JsonProperty("created_at") OffsetDateTime createdAt
    ) {}

    // 컨트롤러에서 count 뺄 거라면 이 타입은 안 써도 됨(남겨둬도 무방)
    public record ListResponse(List<ListItem> items, int count) {}
}
