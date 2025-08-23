package io.github.ssforu.pin4u.features.member.dto;

import java.time.Instant;

public class MemberDtos {
    public record UserResponse(
            Long id, String nickname, String preference_text,
            Instant created_at, Instant updated_at
    ) {}
}
