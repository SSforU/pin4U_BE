package io.github.ssforu.pin4u.features.home.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class HomeDtos {
    private HomeDtos() {}

    public record DashboardResponse(
            @JsonInclude(Include.ALWAYS)
            List<Item> items,
            @JsonInclude(Include.ALWAYS)
            List<Map<String, Object>> groups,
            @JsonInclude(Include.ALWAYS)
            Map<String, Integer> badges
    ) {}

    public record Item(
            String slug,
            String station_name,
            String station_line,
            String road_address_name,
            Integer recommend_count,
            OffsetDateTime created_at,
            String request_message
    ) {}
}
