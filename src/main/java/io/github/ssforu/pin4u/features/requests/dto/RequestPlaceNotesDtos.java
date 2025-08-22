package io.github.ssforu.pin4u.features.requests.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

public final class RequestPlaceNotesDtos {
    private RequestPlaceNotesDtos() {}

    public record Note(
            String nickname,
            String recommendMessage,
            @JsonInclude(JsonInclude.Include.NON_NULL) String imageUrl,
            List<String> tags,
            OffsetDateTime createdAt
    ) {}

    public record Response(
            String externalId,
            String placeName,
            String placeUrl,
            List<Note> notes
    ) {}
}
