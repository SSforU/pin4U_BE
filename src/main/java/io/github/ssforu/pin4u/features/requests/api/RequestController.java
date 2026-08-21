package io.github.ssforu.pin4u.features.requests.api;

import io.github.ssforu.pin4u.common.annotation.LoginUser;
import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.requests.application.RequestService;
import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Requests")
@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;

    @Operation(summary = "요청 생성", security = @SecurityRequirement(name = "uidCookie"))
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @LoginUser(required = true) Long me,
            @Valid @RequestBody RequestDtos.CreateRequest req) {

        try {
            var created = requestService.create(me, req.stationCode(), req.requestMessage(), req.groupSlug());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("request", created);
            return ResponseEntity.created(URI.create("/r/" + created.slug()))
                    .body(ApiResponse.success(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage(), null));
        }
    }

    @Operation(summary = "요청 목록")
    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        List<RequestDtos.ListItem> items = requestService.list();
        Map<String, Object> data = Map.of("items", items);
        return ApiResponse.success(data);
    }

    @Operation(summary = "요청 삭제", security = @SecurityRequirement(name = "uidCookie"))
    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> delete(
            @LoginUser(required = true) Long me,
            @PathVariable String slug) {

        var result = requestService.delete(me, slug);
        return switch (result) {
            case OK -> ResponseEntity.noContent().build();
            case NOT_OWNER -> ResponseEntity.status(403).build();
            case NOT_FOUND -> ResponseEntity.status(404).build();
        };
    }

    @Operation(summary = "요청 오너 닉네임 조회")
    @GetMapping("/{slug}/owner")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOwner(@PathVariable String slug) {
        try {
            var brief = requestService.getOwnerByRequestSlug(slug);
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("owner_user_id", brief.userId(), "owner_nickname", brief.nickname())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", e.getMessage(), Map.of("slug", slug)));
        }
    }
}
