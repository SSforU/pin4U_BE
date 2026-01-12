package io.github.ssforu.pin4u.features.groups.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.groups.application.GroupService;
import io.github.ssforu.pin4u.features.groups.domain.Group;
import io.github.ssforu.pin4u.features.groups.dto.GroupDtos;
import io.github.ssforu.pin4u.features.groups.dto.GroupDtos.MemberRequestListResponse;
import io.github.ssforu.pin4u.features.groups.dto.GroupDtos.MyMemberStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Swagger
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

// ✅ 오너 닉네임 조회용 의존성
import io.github.ssforu.pin4u.features.member.infra.UserRepository;
import io.github.ssforu.pin4u.features.member.domain.User;
import io.github.ssforu.pin4u.features.groups.infra.GroupRepository;

@Tag(name = "Groups")
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService service;
    private final UserRepository userRepository;   // ✅ 추가
    private final GroupRepository groupRepository; // ✅ 추가

    public GroupController(GroupService service,
                           UserRepository userRepository,
                           GroupRepository groupRepository) {
        this.service = service;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
    }

    private Long parseUidOrNull(String uid) {
        if (uid == null || uid.isBlank()) return null;
        try { return Long.valueOf(uid); } catch (NumberFormatException e) { return null; }
    }

    private <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", "unauthorized", null));
    }

    /** 그룹 생성 */
    @Operation(
            summary = "그룹 생성",
            description = "로그인 사용자(me)가 소유한 그룹을 생성합니다.",
            security = @SecurityRequirement(name = "uidCookie")
    )
    @PostMapping
    public ResponseEntity<ApiResponse<GroupDtos.CreateResponse>> create(
            @CookieValue(name = "uid", required = false) String uid,
            @RequestBody(required = false) GroupDtos.CreateRequest body) {

        Long me = parseUidOrNull(uid);
        if (me == null) return unauthorized();

        if (body == null || body.name() == null || body.name().isBlank()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("BAD_REQUEST", "name_required", null)
            );
        }

        Group g = service.createGroup(me, body.name(), body.image_url());
        GroupDtos.CreateResponse res = new GroupDtos.CreateResponse(
                g.getId(), g.getSlug(), g.getName(), g.getImageUrl()
        );
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    /** 멤버 요청/승인/거절 */
    @Operation(
            summary = "멤버 요청/승인/거절",
            description = "`action = request | approve | reject` (승인/거절은 owner만 가능)",
            security = @SecurityRequirement(name = "uidCookie")
    )
    @PostMapping("/{group_slug}/members")
    public ResponseEntity<ApiResponse<GroupDtos.MemberActionResponse>> memberAction(
            @CookieValue(name = "uid", required = false) String uid,
            @PathVariable("group_slug") String groupSlug,
            @RequestBody(required = false) GroupDtos.MemberActionRequest body) {

        Long me = parseUidOrNull(uid);
        if (me == null) return unauthorized();

        if (body == null) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("BAD_REQUEST", "body_required", null)
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
                    return ResponseEntity.badRequest().body(
                            ApiResponse.error("BAD_REQUEST", "user_id_required", null)
                    );
                }
                service.approveMember(groupSlug, me, body.user_id());
                return ResponseEntity.ok(ApiResponse.success(new GroupDtos.MemberActionResponse("approved")));
            }
            case "reject" -> {
                if (body.user_id() == null) {
                    return ResponseEntity.badRequest().body(
                            ApiResponse.error("BAD_REQUEST", "user_id_required", null)
                    );
                }
                service.rejectMember(groupSlug, me, body.user_id());
                return ResponseEntity.ok(ApiResponse.success(new GroupDtos.MemberActionResponse("rejected")));
            }
            default -> {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("BAD_REQUEST", "invalid_action", null)
                );
            }
        }
    }

    /** 내 멤버십 상태 */
    @Operation(
            summary = "내 멤버십 상태",
            description = "해당 그룹에서 내 상태를 조회합니다. (none|pending|approved / role)",
            security = @SecurityRequirement(name = "uidCookie")
    )
    @GetMapping("/{group_slug}/members/me/status")
    public ResponseEntity<ApiResponse<MyMemberStatusResponse>> myStatus(
            @CookieValue(name = "uid", required = false) String uid,
            @PathVariable("group_slug") String groupSlug
    ) {
        Long me = parseUidOrNull(uid);
        if (me == null) return unauthorized();

        var res = service.getMyStatus(groupSlug, me);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    /** 그룹 멤버요청 목록(알림 대체) — owner 전용 */
    @Operation(
            summary = "그룹 멤버요청 목록",
            description = "그룹 소유자가 대기중(pending)/승인(approved)/전체(all) 멤버 요청 현황을 조회합니다.",
            security = @SecurityRequirement(name = "uidCookie")
    )
    @GetMapping("/{group_slug}/members/requests")
    public ResponseEntity<ApiResponse<MemberRequestListResponse>> listRequests(
            @CookieValue(name = "uid", required = false) String uid,
            @PathVariable("group_slug") String groupSlug,
            @RequestParam(name = "status", required = false, defaultValue = "pending") String status,
            @RequestParam(name = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        Long me = parseUidOrNull(uid);
        if (me == null) return unauthorized();

        var res = service.listMemberRequests(groupSlug, me, status, limit);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    // ✅ 신규: 그룹 공유 링크에서 오너 닉네임만 빠르게 조회 (비인증)
    @Operation(summary = "그룹 오너 닉네임 조회", description = "그룹 공유 링크에서 지도 소유자 닉네임을 노출하는 용도.")
    @GetMapping("/{group_slug}/owner")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getGroupOwner(
            @PathVariable("group_slug") String groupSlug
    ) {
        var g = groupRepository.findBySlug(groupSlug).orElse(null);
        if (g == null) {
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", "group not found", java.util.Map.of("group_slug", groupSlug))
            );
        }
        Long uid = g.getOwnerUserId();
        String nick = userRepository.findById(uid)
                .map(User::getNickname)
                .filter(n -> n != null && !n.isBlank())
                .orElse("사용자");
        return ResponseEntity.ok(
                ApiResponse.success(java.util.Map.of("owner_user_id", uid, "owner_nickname", nick))
        );
    }
}
