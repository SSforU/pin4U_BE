package io.github.ssforu.pin4u.features.notifications.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public class NotificationDtos {

    public record NotificationItem(
            @Schema(example = "gm:10:42:1737667200")
            String id,

            // 프론트가 camelCase로 요구: 명시적으로 강제
            @JsonProperty("requesterName")
            @Schema(example = "박숭실")
            String requesterName,

            @Schema(example = "9007199254740991")
            Long requester_id,

            @JsonProperty("groupName")
            @Schema(example = "그룹명")
            String groupName,

            @Schema(example = "group-1")
            String group_slug,

            @Schema(example = "2025-09-23T00:12:34Z")
            Instant created_at,

            @Schema(allowableValues = {"pending","approved"}, example = "pending")
            String status
    ) {}

    public record NotificationListResponse(
            List<NotificationItem> items,
            Integer count
    ) {}
}
