package io.github.ssforu.pin4u.features.groups.api;

import io.github.ssforu.pin4u.common.annotation.LoginUser;
import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.groups.application.GroupMapService;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
            description = "그룹에 속한 모든 요청을 합산하여 장소 리스트를 반환합니다.",
            security = @SecurityRequirement(name = "uidCookie")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            content = @Content(schema = @Schema(implementation = RequestDetailDtos.RequestDetailResponse.class))
    )
    @GetMapping("/{group_slug}/map")
    public ResponseEntity<ApiResponse<RequestDetailDtos.RequestDetailResponse>> map(
            @LoginUser(required = true) Long me,
            @PathVariable("group_slug") String groupSlug,
            @RequestParam(value = "limit", required = false) Integer limit) {

        var data = service.getGroupMapAsRequestDetail(groupSlug, me, limit);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
