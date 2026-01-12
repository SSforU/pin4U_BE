package io.github.ssforu.pin4u.features.groups.api;

import io.github.ssforu.pin4u.features.groups.infra.GroupRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// Swagger (문서 표시용 - 선택)
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Groups")
@RestController
@RequestMapping("/api/groups")
public class GroupNotesController {

    private final GroupRepository groups;
    private final RequestRepository requests;

    public GroupNotesController(GroupRepository groups, RequestRepository requests) {
        this.groups = groups;
        this.requests = requests;
    }

    // 시연용: 인증/권한 검사는 생략
    @Operation(summary = "그룹지도 장소 노트 조회(리다이렉트)",
            description = "그룹 slug와 external_id로 원본 request.slug를 찾아 기존 /api/requests/{slug}/places/notes 로 302 리다이렉트")
    @GetMapping("/{group_slug}/places/notes")
    public ResponseEntity<Void> redirectNotes(
            @PathVariable("group_slug") String groupSlug,
            @RequestParam("external_id") String externalId
    ) {
        var g = groups.findBySlug(groupSlug).orElse(null);
        if (g == null) {
            return ResponseEntity.notFound().build();
        }
        String reqSlug = requests.findAnyRequestSlugByGroupIdAndExternalId(g.getId(), externalId);
        if (reqSlug == null) {
            return ResponseEntity.notFound().build();
        }
        String q = URLEncoder.encode(externalId, StandardCharsets.UTF_8);
        URI to = URI.create("/api/requests/" + reqSlug + "/places/notes?external_id=" + q);
        return ResponseEntity.status(302).location(to).build(); // 302 Found
    }
}
