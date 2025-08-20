// src/main/java/io/github/ssforu/pin4u/features/requests/dto/RequestDtos.java
package io.github.ssforu.pin4u.features.requests.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

public final class RequestDtos {
    private RequestDtos() {}

    // 요청 바디
    public record CreateRequest(
            @JsonProperty("owner_nickname") String ownerNickname,
            @JsonProperty("station_code") String stationCode,
            @JsonProperty("request_message") String requestMessage
    ) {}

    // 생성 결과
    public record CreatedRequestDTO(
            String slug,
            String ownerNickname,
            String stationCode,
            String requestMessage,
            OffsetDateTime createdAt
    ) {}

    // 리스트 아이템
    public record ListItem(
            String slug,
            String ownerNickname,
            String stationCode,
            String requestMessage,
            int recommendCount,
            OffsetDateTime createdAt
    ) {}

    // 컨트롤러 응답 포맷
    public record ListResponse(List<ListItem> items, int count) {}
}
