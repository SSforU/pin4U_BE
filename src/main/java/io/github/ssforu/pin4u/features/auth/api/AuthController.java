package io.github.ssforu.pin4u.features.auth.api;

import io.github.ssforu.pin4u.features.auth.application.AuthService;
import io.github.ssforu.pin4u.features.auth.dto.AuthDtos;
import io.github.ssforu.pin4u.features.member.domain.User;
import io.github.ssforu.pin4u.features.member.infra.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Optional;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository users;

    @Value("${app.cookies.crossSite:true}")
    private boolean crossSite;

    @Value("${app.cookies.domain:}")
    private String cookieDomain;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @Operation(
            summary = "카카오 로그인",
            description = "프론트에서 전달한 Kakao access_token을 검증하고 uid 쿠키를 발급합니다."
    )
    @PostMapping("/kakao/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(@RequestBody AuthDtos.KakaoLoginRequest body,
                                                        HttpServletResponse res) {
        if (body == null || body.accessToken() == null || body.accessToken().isBlank()) {
            throw new IllegalArgumentException("access_token_required");
        }

        var out = authService.loginWithKakaoToken(body.accessToken());

        var builder = ResponseCookie.from("uid", String.valueOf(out.user().id()))
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite(crossSite ? "None" : "Lax")
                .secure(crossSite);

        // 교차 사이트면 CHIPS(Partitioned) 부여
        if (crossSite) {
            builder = builder.partitioned(true);
        }

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder = builder.domain(cookieDomain.trim());
        }

        res.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
        return ResponseEntity.ok(out);
    }

    @Operation(
            summary = "현재 로그인 사용자 조회",
            description = "uid 쿠키 기준으로 로그인 사용자를 반환합니다. 없으면 204.",
            security = @SecurityRequirement(name = "uidCookie")
    )
    @GetMapping("/me")
    public ResponseEntity<AuthDtos.LoginUser> me(@CookieValue(name = "uid", required = false) String uid) {
        if (uid == null || uid.isBlank()) return ResponseEntity.noContent().build();

        try {
            Long id = Long.valueOf(uid);
            Optional<User> u = users.findById(id);
            return u.map(user -> ResponseEntity.ok(new AuthDtos.LoginUser(user.getId(), user.getNickname())))
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (NumberFormatException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @Operation(summary = "로그아웃", description = "uid 쿠키를 만료시킵니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse res) {
        var builder = ResponseCookie.from("uid", "")
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite(crossSite ? "None" : "Lax")
                .secure(crossSite);

        // 교차 사이트면 CHIPS(Partitioned) 부여
        if (crossSite) {
            builder = builder.partitioned(true);
        }

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder = builder.domain(cookieDomain.trim());
        }

        res.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
        return ResponseEntity.noContent().build();
    }
}
