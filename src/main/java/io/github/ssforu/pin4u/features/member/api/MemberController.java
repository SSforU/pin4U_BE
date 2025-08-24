package io.github.ssforu.pin4u.features.member.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.member.application.MemberService;
import io.github.ssforu.pin4u.features.member.dto.MemberDtos;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@CrossOrigin
@RestController
public class MemberController {
    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    // 기존 /api/user 유지 + /api/user/1 별칭 추가 (항상 고정 유저 반환)
    @GetMapping({"/api/user", "/api/user/1"})
    public ApiResponse<Map<String, Object>> getUser() {
        MemberDtos.UserResponse user = service.getFixedUser(); // 내부적으로 id=1 고정
        return ApiResponse.success(Map.of("user", user));
    }
}
