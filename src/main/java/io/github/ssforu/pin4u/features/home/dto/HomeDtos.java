package io.github.ssforu.pin4u.features.home.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class HomeDtos {
    private HomeDtos() {}

    // ✅ 홈 대시보드 응답: items는 홈 전용 Item으로 교체
    public record DashboardResponse(
            List<Item> items,
            List<Map<String, Object>> groups, // 1차 버전: 빈 리스트 유지
            Map<String, Integer> badges       // 예: {"group_owner_pending":0}
    ) {}

    // ✅ 홈 전용 아이템: request_message 포함
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
