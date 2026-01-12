package io.github.ssforu.pin4u.features.requests.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.requests.application.RequestService;
import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@Tag(name = "Requests")
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requestService;
    public RequestController(RequestService requestService) { this.requestService = requestService; }

    @Operation(summary = "요청 생성", description = "Slug가 발급되고 /r/{slug}로 접근 가능합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @CookieValue(name = "uid", required = false) String uid,
            @Valid @RequestBody RequestDtos.CreateRequest req
    ) {
        Long me = null;
        try { me = (uid == null || uid.isBlank()) ? null : Long.valueOf(uid); } catch (NumberFormatException ignore) {}
        if (me == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("UNAUTHORIZED", "login required", null));
        }

        try {
            var created = requestService.create(me, req.stationCode(), req.requestMessage(), /*groupSlug*/ req.groupSlug());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("request", created);
            return ResponseEntity.created(URI.create("/r/" + created.slug()))
                    .body(ApiResponse.success(body));
        } catch (IllegalArgumentException e) {
            // ✅ 핵심: 서비스에서 던진 검증 실패는 전부 400으로 고정
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage(), null));
        }
    }

    @Operation(summary = "요청 목록", description = "최신 순서로 요청 리스트를 반환합니다.")
    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        List<RequestDtos.ListItem> items = requestService.list();
        Map<String, Object> data = Map.of("items", items);
        return ApiResponse.success(data);
    }

    @Operation(
            summary = "요청 삭제",
            description = "요청 소유자만 삭제할 수 있습니다. 관련 집계/메모는 DB FK `ON DELETE CASCADE`로 함께 삭제됩니다.",
            security = @SecurityRequirement(name = "uidCookie")
    )
    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> delete(
            @CookieValue(name = "uid", required = false) String uid,
            @PathVariable String slug
    ) {
        Long me;
        try { me = (uid == null || uid.isBlank()) ? null : Long.valueOf(uid); } catch (NumberFormatException e) { me = null; }
        if (me == null) return ResponseEntity.status(401).build();

        var result = requestService.delete(me, slug);
        return switch (result) {
            case OK -> ResponseEntity.noContent().build();        // 204
            case NOT_OWNER -> ResponseEntity.status(403).build(); // 403
            case NOT_FOUND -> ResponseEntity.status(404).build(); // 404
        };
    }

    // ✅ 신규: 개인지도(요청) 오너 닉네임 조회 (비인증)
    @Operation(summary = "요청 오너 닉네임 조회", description = "요청 slug 소유자의 user_id와 nickname을 반환합니다.")
    @GetMapping("/{slug}/owner")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOwner(@PathVariable String slug) {
        try {
            var brief = requestService.getOwnerByRequestSlug(slug);
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("owner_user_id", brief.userId(), "owner_nickname", brief.nickname())
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", e.getMessage(), Map.of("slug", slug))
            );
        }
    }
}
