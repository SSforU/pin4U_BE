package io.github.ssforu.pin4u.features.requests.dto;

import java.time.Instant;
import java.util.List;

public final class RequestPlaceNotesDtos {

    private RequestPlaceNotesDtos() {}

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
