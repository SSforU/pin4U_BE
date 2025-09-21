package io.github.ssforu.pin4u.features.member.api;

import io.github.ssforu.pin4u.features.member.application.MemberService;
import io.github.ssforu.pin4u.features.member.domain.User;
import io.github.ssforu.pin4u.features.member.dto.MemberDtos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

// ✅ Swagger 문서용 어노테이션 import
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@Tag(name = "Member")
@RestController
@RequestMapping("/api")
public class MemberMeController {

    private final MemberService service;

    public MemberMeController(MemberService service) {
        this.service = service;
    }

    /** 현재 로그인 사용자 조회 (쿠키 uid 기반) — 로그인 안 되어 있으면 204 */
    @Operation(
            summary = "내 프로필 조회",
            description = "uid 쿠키가 있으면 내 프로필을, 없으면 204를 반환합니다.",
            security = @SecurityRequirement(name = "uidCookie") // ✅ Swagger 상 인증표시(문서 전용)
    )
    @GetMapping("/me")
    public ResponseEntity<MemberDtos.UserResponse> me(
            @CookieValue(name = "uid", required = false) String uid) {

        Long id = parseUid(uid);
        if (id == null) return ResponseEntity.noContent().build();

        Optional<User> u = service.findById(id);
        return u.map(user -> ResponseEntity.ok(toDto(user)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 닉네임 변경 (2~16자). 미로그인 시 401 */
    @Operation(
            summary = "내 닉네임 변경",
            description = "2~16자 제약. 로그인(쿠키 uid) 필요.",
            security = @SecurityRequirement(name = "uidCookie") // ✅ Swagger 상 인증표시(문서 전용)
    )
    @PatchMapping("/me")
    public ResponseEntity<MemberDtos.UserResponse> updateNickname(
            @CookieValue(name = "uid", required = false) String uid,
            @RequestBody MemberDtos.NicknamePatch req) {

        if (req == null || req.nickname() == null) {
            throw new IllegalArgumentException("nickname_required");
        }
        String nick = req.nickname().trim();
        if (nick.length() < 2 || nick.length() > 16) {
            throw new IllegalArgumentException("nickname_length_2_to_16");
        }

        Long id = parseUid(uid);
        if (id == null) return ResponseEntity.status(401).build();

        User updated = service.updateNickname(id, nick);
        return ResponseEntity.ok(toDto(updated));
    }

    // ===== helpers =====
    private static Long parseUid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return Long.valueOf(raw.trim()); } catch (Exception e) { return null; }
    }
    private static MemberDtos.UserResponse toDto(User u) {
        return new MemberDtos.UserResponse(
                u.getId(), u.getNickname(), u.getPreferenceText(),
                u.getCreatedAt(), u.getUpdatedAt()
        );
    }
}
