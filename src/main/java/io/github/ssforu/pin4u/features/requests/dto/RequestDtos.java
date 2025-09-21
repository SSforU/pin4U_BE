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
            String stationCode,
            String requestMessage,
            String groupSlug
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreatedRequestDTO(
            String slug,
            String stationCode,
            String requestMessage,
            OffsetDateTime createdAt
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ListItem(
            String slug,
            String stationName,
            String stationLine,
            String roadAddressName,
            int    recommendCount,
            OffsetDateTime createdAt
    ) {}

    public record ListResponse(List<ListItem> items, int count) {}
}
