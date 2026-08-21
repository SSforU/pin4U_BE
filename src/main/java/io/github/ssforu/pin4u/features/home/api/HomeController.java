package io.github.ssforu.pin4u.features.home.api;

import io.github.ssforu.pin4u.common.annotation.LoginUser;
import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.home.application.HomeService;
import io.github.ssforu.pin4u.features.home.dto.HomeDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
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
            @LoginUser(required = false) Long me) {

        if (me == null) return ResponseEntity.noContent().build();

        var data = homeService.dashboard(me);
        var safe = new HomeDtos.DashboardResponse(
                data.items()  != null ? data.items()  : List.of(),
                data.groups() != null ? data.groups() : List.of(),
                data.badges() != null ? data.badges() : Map.of("group_owner_pending", 0)
        );
        return ResponseEntity.ok(ApiResponse.success(safe));
    }
}
