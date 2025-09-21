package io.github.ssforu.pin4u.features.groups.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class GroupDtos {

    @Schema(name = "GroupCreateRequest", description = "그룹 생성 요청 바디 (문서용 확장 필드 포함)")
    public record CreateRequest(
            @Schema(description = "그룹 이름", example = "우리모임")
            String name,
            @Schema(description = "그룹 이미지 URL", example = "https://example.com/group.png")
            String image_url,

            // === 아래 4개는 스웨거 문서 노출용(선택 입력) ===
            @Schema(description = "(문서용) 역 이름", example = "숭실대입구")
            String station_name,
            @Schema(description = "(문서용) 호선", example = "7호선")
            String station_line,
            @Schema(description = "(문서용) 요청 메시지", example = "조용한 카페 찾습니다")
            String request_message,
            @Schema(description = "(문서용) 희망 그룹 슬러그(중복 불가)", example = "woori-1")
            String group_slug
    ) {}

    public record CreateResponse(Long id, String slug, String name, String image_url) {}

    // 하나의 엔드포인트에서 "요청"과 "승인"을 처리
    // action: "request" | "approve"
    // approve일 때만 user_id 필요(승인 대상)
    public record MemberActionRequest(String action, Long user_id) {}
    public record MemberActionResponse(String status) {}
}
