package io.github.ssforu.pin4u.features.groups.api;

import io.github.ssforu.pin4u.common.response.ApiResponse; // ← 이건 유지
import io.github.ssforu.pin4u.features.groups.application.GroupMapService;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos;
import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.responses.ApiResponse;  // ← 이 줄은 삭제(충돌 원인)
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
// ↓ 새로 필요
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Groups")
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupMapController {

    private final GroupMapService service;

    @Operation(
            summary = "그룹지도(개인지도 스키마)",
            description = "그룹에 속한 모든 요청을 합산하여 장소 리스트를 반환합니다. " +
                    "응답 스키마는 개인지도 상세(/api/requests/{slug})와 동일합니다.",
            security = @SecurityRequirement(name = "uidCookie")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse( // ← 완전수식으로 사용
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = RequestDetailDtos.RequestDetailResponse.class))
    )
    @GetMapping("/{group_slug}/map")
    public ResponseEntity<ApiResponse<RequestDetailDtos.RequestDetailResponse>> map(
            @CookieValue(name = "uid", required = false) String uid,
            @PathVariable("group_slug") String groupSlug,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        Long me;
        try {
            me = (uid == null || uid.isBlank()) ? null : Long.valueOf(uid);
        } catch (NumberFormatException e) {
            me = null;
        }
        if (me == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("UNAUTHORIZED", "unauthorized", null));
        }

        var data = service.getGroupMapAsRequestDetail(groupSlug, me, limit);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
