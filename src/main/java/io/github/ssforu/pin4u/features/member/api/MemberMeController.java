package io.github.ssforu.pin4u.features.member.api;

import io.github.ssforu.pin4u.common.annotation.LoginUser; // Import 추가
import io.github.ssforu.pin4u.features.member.application.MemberService;
import io.github.ssforu.pin4u.features.member.domain.User;
import io.github.ssforu.pin4u.features.member.dto.MemberDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Tag(name = "Member")
@RestController
@RequestMapping("/api")
public class MemberMeController {

    private final MemberService service;

    public MemberMeController(MemberService service) {
        this.service = service;
    }

    /** 현재 로그인 사용자 조회 */
    @Operation(summary = "내 프로필 조회", description = "uid 쿠키가 있으면 내 프로필 반환", security = @SecurityRequirement(name = "uidCookie"))
    @GetMapping("/me")
    public ResponseEntity<MemberDtos.UserResponse> me(
            @LoginUser(required = false) Long userId // 리팩토링 된 부분
    ) {
        if (userId == null) return ResponseEntity.noContent().build();

        Optional<User> u = service.findById(userId);
        return u.map(user -> ResponseEntity.ok(toDto(user)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 닉네임 변경 */
    @Operation(summary = "내 닉네임 변경", description = "로그인 필요", security = @SecurityRequirement(name = "uidCookie"))
    @PatchMapping("/me")
    public ResponseEntity<MemberDtos.UserResponse> updateNickname(
            @LoginUser(required = true) Long userId, // 리팩토링: 필수 체크 자동화
            @RequestBody MemberDtos.NicknamePatch req) {

        if (req == null || req.nickname() == null) {
            throw new IllegalArgumentException("nickname_required");
        }
        String nick = req.nickname().trim();
        if (nick.length() < 2 || nick.length() > 16) {
            throw new IllegalArgumentException("nickname_length_2_to_16");
        }

        // userId null 체크 불필요 (required=true 덕분)
        User updated = service.updateNickname(userId, nick);
        return ResponseEntity.ok(toDto(updated));
    }

    // parseUid 헬퍼 메서드 삭제 가능

    private static MemberDtos.UserResponse toDto(User u) {
        return new MemberDtos.UserResponse(
                u.getId(), u.getNickname(), u.getPreferenceText(),
                u.getCreatedAt(), u.getUpdatedAt()
        );
    }
}