package io.github.ssforu.pin4u.features.groups.api;

import io.github.ssforu.pin4u.common.annotation.LoginUser;
import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.groups.application.GroupService;
import io.github.ssforu.pin4u.features.groups.domain.Group;
import io.github.ssforu.pin4u.features.groups.dto.GroupDtos;
import io.github.ssforu.pin4u.features.groups.dto.GroupDtos.MemberRequestListResponse;
import io.github.ssforu.pin4u.features.groups.dto.GroupDtos.MyMemberStatusResponse;
import io.github.ssforu.pin4u.features.member.infra.UserRepository;
import io.github.ssforu.pin4u.features.member.domain.User;
import io.github.ssforu.pin4u.features.groups.infra.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@Tag(name = "Groups")
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService service;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    @Operation(summary = "그룹 생성", security = @SecurityRequirement(name = "uidCookie"))
    @PostMapping
    public ResponseEntity<ApiResponse<GroupDtos.CreateResponse>> create(
            @LoginUser(required = true) Long me,
            @RequestBody(required = false) GroupDtos.CreateRequest body) {

        if (body == null || body.name() == null || body.name().isBlank()) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("BAD_REQUEST", "name_required", null));
        }

        Group g = service.createGroup(me, body.name(), body.image_url());
        GroupDtos.CreateResponse res = new GroupDtos.CreateResponse(
                g.getId(), g.getSlug(), g.getName(), g.getImageUrl());
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    @Operation(summary = "멤버 요청/승인/거절", security = @SecurityRequirement(name = "uidCookie"))
    @PostMapping("/{group_slug}/members")
    public ResponseEntity<ApiResponse<GroupDtos.MemberActionResponse>> memberAction(
            @LoginUser(required = true) Long me,
            @PathVariable("group_slug") String groupSlug,
            @RequestBody(required = false) GroupDtos.MemberActionRequest body) {

        if (body == null) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("BAD_REQUEST", "body_required", null));
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
                            ApiResponse.error("BAD_REQUEST", "user_id_required", null));
                }
                service.approveMember(groupSlug, me, body.user_id());
                return ResponseEntity.ok(ApiResponse.success(new GroupDtos.MemberActionResponse("approved")));
            }
            case "reject" -> {
                if (body.user_id() == null) {
                    return ResponseEntity.badRequest().body(
                            ApiResponse.error("BAD_REQUEST", "user_id_required", null));
                }
                service.rejectMember(groupSlug, me, body.user_id());
                return ResponseEntity.ok(ApiResponse.success(new GroupDtos.MemberActionResponse("rejected")));
            }
            default -> {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("BAD_REQUEST", "invalid_action", null));
            }
        }
    }

    @Operation(summary = "내 멤버십 상태", security = @SecurityRequirement(name = "uidCookie"))
    @GetMapping("/{group_slug}/members/me/status")
    public ResponseEntity<ApiResponse<MyMemberStatusResponse>> myStatus(
            @LoginUser(required = true) Long me,
            @PathVariable("group_slug") String groupSlug) {

        var res = service.getMyStatus(groupSlug, me);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    @Operation(summary = "그룹 멤버요청 목록", security = @SecurityRequirement(name = "uidCookie"))
    @GetMapping("/{group_slug}/members/requests")
    public ResponseEntity<ApiResponse<MemberRequestListResponse>> listRequests(
            @LoginUser(required = true) Long me,
            @PathVariable("group_slug") String groupSlug,
            @RequestParam(name = "status", required = false, defaultValue = "pending") String status,
            @RequestParam(name = "limit", required = false, defaultValue = "20") Integer limit) {

        var res = service.listMemberRequests(groupSlug, me, status, limit);
        return ResponseEntity.ok(ApiResponse.success(res));
    }

    @Operation(summary = "그룹 오너 닉네임 조회")
    @GetMapping("/{group_slug}/owner")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getGroupOwner(
            @PathVariable("group_slug") String groupSlug) {

        var g = groupRepository.findBySlug(groupSlug).orElse(null);
        if (g == null) {
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", "group not found",
                            java.util.Map.of("group_slug", groupSlug)));
        }
        Long uid = g.getOwnerUserId();
        String nick = userRepository.findById(uid)
                .map(User::getNickname)
                .filter(n -> n != null && !n.isBlank())
                .orElse("사용자");
        return ResponseEntity.ok(
                ApiResponse.success(java.util.Map.of("owner_user_id", uid, "owner_nickname", nick)));
    }
}
