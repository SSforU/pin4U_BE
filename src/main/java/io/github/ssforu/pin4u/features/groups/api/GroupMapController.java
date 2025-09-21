package io.github.ssforu.pin4u.features.groups.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.groups.application.GroupMapService;
import io.github.ssforu.pin4u.features.groups.dto.GroupMapDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
            summary = "그룹지도",
            description = "그룹에 속한 모든 요청을 합산하여 장소 리스트를 반환합니다.",
            security = @SecurityRequirement(name = "uidCookie")
    )
    @GetMapping("/{group_slug}/map")
    public ResponseEntity<ApiResponse<GroupMapDtos.Response>> map(
            @CookieValue(name = "uid", required = false) String uid,
            @PathVariable("group_slug") String groupSlug,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        Long me;
        try { me = (uid == null || uid.isBlank()) ? null : Long.valueOf(uid); } catch (NumberFormatException e) { me = null; }
        if (me == null) {
            // 기존 컨트롤러들과 일관되게 ApiResponse.error 형태로 반환
            return ResponseEntity.status(401).body(ApiResponse.error("UNAUTHORIZED", "unauthorized", null));
        }

        var data = service.getGroupMap(groupSlug, me, limit);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
