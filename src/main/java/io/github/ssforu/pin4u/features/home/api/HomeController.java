package io.github.ssforu.pin4u.features.home.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.home.application.HomeService;
import io.github.ssforu.pin4u.features.home.dto.HomeDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Home")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(
            summary = "홈 대시보드",
            description = "로그인 사용자의 요청 요약 목록을 반환합니다.",
            security = @SecurityRequirement(name = "uidCookie")
    )
    @GetMapping
    public ResponseEntity<ApiResponse<HomeDtos.DashboardResponse>> get(
            @CookieValue(name = "uid", required = false) String uid
    ) {
        Long me;
        try { me = (uid == null || uid.isBlank()) ? null : Long.valueOf(uid); } catch (NumberFormatException e) { me = null; }
        if (me == null) return ResponseEntity.noContent().build(); // 204

        var data = homeService.dashboard(me);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
