package io.github.ssforu.pin4u.features.requests.dto;

import java.time.Instant;
import java.util.List;

public final class RequestPlaceNotesDtos {

    private RequestPlaceNotesDtos() {}

    // Theme 2에서 누락되었던 요청 DTO 추가
    public record CreateRequest(
            String stationCode,
            Long groupId,
            String message,
            List<PlaceItem> places
    ) {}

    // CreateRequest 내부에서 사용하는 장소 ID 래퍼
    public record PlaceItem(
            Long placeId
    ) {}

    public record Note(
            String nickname,
            String recommendMessage,
            String imageUrl,
            List<String> tags,
            Instant createdAt
    ) {}

    public record Response(
            String externalId,
            String placeName,
            String placeUrl,
            List<Note> notes
    ) {}
}