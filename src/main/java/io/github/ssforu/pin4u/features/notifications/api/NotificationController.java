package io.github.ssforu.pin4u.features.notifications.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.notifications.application.NotificationService;
import io.github.ssforu.pin4u.features.notifications.dto.NotificationDtos.NotificationListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    private Long parseUidOrNull(String uid) {
        if (uid == null || uid.isBlank()) return null;
        try { return Long.valueOf(uid); } catch (NumberFormatException e) { return null; }
    }
    private <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", "unauthorized", null));
    }

    @Operation(
            summary = "알림(멤버요청) 목록",
            description = "로그인 사용자가 소유한 모든 그룹의 멤버요청을 통합 조회합니다.",
            security = @SecurityRequirement(name = "uidCookie")
    )
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<NotificationListResponse>> listNotifications(
            @CookieValue(name = "uid", required = false) String uid,
            @RequestParam(name = "status", required = false, defaultValue = "pending") String status,
            @RequestParam(name = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        Long me = parseUidOrNull(uid);
        if (me == null) return unauthorized();

        var data = service.listNotifications(me, status, limit);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
