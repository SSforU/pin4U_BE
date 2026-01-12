package io.github.ssforu.pin4u.features.groups.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public class GroupDtos {

    @Schema(name = "GroupCreateRequest", description = "그룹 생성 요청 바디 (문서용 확장 필드 포함)")
    public record CreateRequest(
            @Schema(description = "그룹 이름", example = "우리모임")
            String name,
            @Schema(description = "그룹 이미지 URL", example = "https://example.com/group.png")
            String image_url,

            // 문서용(옵션)
            @Schema(description = "(문서용) 역 이름", example = "숭실대입구") String station_name,
            @Schema(description = "(문서용) 호선", example = "7호선") String station_line,
            @Schema(description = "(문서용) 요청 메시지", example = "조용한 카페 찾습니다") String request_message,
            @Schema(description = "(문서용) 희망 그룹 슬러그(중복 불가)", example = "woori-1") String group_slug
    ) {}

    public record CreateResponse(Long id, String slug, String name, String image_url) {}

    /** 멤버 요청/승인/거절 공용 바디 */
    public record MemberActionRequest(String action, Long user_id) {}
    public record MemberActionResponse(String status) {}

    /** 내 멤버십 상태 조회 응답 */
    public record MyMemberStatusResponse(
            String status,   // "none" | "pending" | "approved"
            String role      // "owner" | "member" | null
    ) {}

    /** 알림 리스트(=그룹의 멤버 요청 목록) 한 항목 */
    public record MemberRequestItem(
            Long user_id,
            String nickname,
            String status,         // "pending" | "approved"
            Instant requested_at,
            Instant approved_at
    ) {}

    /** 알림 리스트 응답 */
    public record MemberRequestListResponse(
            List<MemberRequestItem> items,
            Integer count
    ) {}
}
