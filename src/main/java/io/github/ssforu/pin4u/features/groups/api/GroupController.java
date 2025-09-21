package io.github.ssforu.pin4u.features.groups.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.groups.application.GroupService;
import io.github.ssforu.pin4u.features.groups.domain.Group;
import io.github.ssforu.pin4u.features.groups.dto.GroupDtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// ✅ Swagger 문서용 어노테이션 import
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@Tag(name = "Groups") // ✅ 문서 그룹
@RestController
@RequestMapping("/api/groups") // ✅ 공통 prefix로 명확화
public class GroupController {

    private final GroupService service;

    public GroupController(GroupService service) { this.service = service; }

    private Long parseUidOrNull(String uid) {
        if (uid == null || uid.isBlank()) return null;
        try { return Long.valueOf(uid); } catch (NumberFormatException e) { return null; }
    }

    // ✅ 제네릭으로 변경: 어떤 응답타입에도 맞춰 401 반환 가능
    private <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        // 🔧 컴파일 에러 방지: fail(...) 대신 error(...) 사용 (응답 의미 동일)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", "unauthorized", null)); // ★ 변경
    }

    /** 그룹 생성: POST /api/groups */
    @Operation(
            summary = "그룹 생성",
            description = "로그인 사용자(me)가 소유한 그룹을 생성합니다.",
            security = @SecurityRequirement(name = "uidCookie") // ✅ Swagger 상 인증표시(문서 전용)
    )
    @PostMapping
    public ResponseEntity<ApiResponse<GroupDtos.CreateResponse>> create(
            @CookieValue(name = "uid", required = false) String uid,
            @RequestBody(required = false) GroupDtos.CreateRequest body) {

        Long me = parseUidOrNull(uid);
        if (me == null) return unauthorized();

        if (body == null || body.name() == null || body.name().isBlank()) {
            // 🔧 fail → error (동일 의미)
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("BAD_REQUEST", "name_required", null) // ★ 변경
            );
        }

        Group g = service.createGroup(me, body.name(), body.image_url());
        GroupDtos.CreateResponse res = new GroupDtos.CreateResponse(
                g.getId(), g.getSlug(), g.getName(), g.getImageUrl()
        );
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    /** 멤버 요청/승인: POST /api/groups/{group_slug}/members  (action: request | approve) */
    @Operation(
            summary = "멤버 요청/승인",
            description = "`action = request | approve` (승인은 owner만 가능)",
            security = @SecurityRequirement(name = "uidCookie") // ✅ Swagger 상 인증표시(문서 전용)
    )
    @PostMapping("/{group_slug}/members")
    public ResponseEntity<ApiResponse<GroupDtos.MemberActionResponse>> memberAction(
            @CookieValue(name = "uid", required = false) String uid,
            @PathVariable("group_slug") String groupSlug, // ✅ 경로 변수명 일치
            @RequestBody(required = false) GroupDtos.MemberActionRequest body) {

        Long me = parseUidOrNull(uid);
        if (me == null) return unauthorized();

        if (body == null) {
            // 🔧 fail → error (동일 의미)
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("BAD_REQUEST", "body_required", null) // ★ 변경
            );
        }

        String action = (body.action() == null ? "" : body.action().trim().toLowerCase());
        switch (action) {
            case "request" -> {
                service.requestJoin(groupSlug, me);
                return ResponseEntity.ok(ApiResponse.success(new GroupDtos.MemberActionResponse("requested")));
            }
            case "approve" -> {
                if (body.user_id() == null) {
                    // 🔧 fail → error (동일 의미)
                    return ResponseEntity.badRequest().body(
                            ApiResponse.error("BAD_REQUEST", "user_id_required", null) // ★ 변경
                    );
                }
                service.approveMember(groupSlug, me, body.user_id());
                return ResponseEntity.ok(ApiResponse.success(new GroupDtos.MemberActionResponse("approved")));
            }
            default -> {
                // 🔧 fail → error (동일 의미)
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("BAD_REQUEST", "invalid_action", null) // ★ 변경
                );
            }
        }
    }
}
