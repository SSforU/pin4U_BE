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

@RestController
@RequestMapping("/api/auth") // ✅ API 명세와 통일: /api/auth/**
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

    /** 프론트가 준 access_token 검증 → upsert → uid 쿠키 세팅 */
    @PostMapping("/kakao/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(@RequestBody AuthDtos.KakaoLoginRequest body,
                                                        HttpServletResponse res) {
        if (body == null || body.accessToken() == null || body.accessToken().isBlank()) {
            throw new IllegalArgumentException("access_token_required");
        }

        var out = authService.loginWithKakaoToken(body.accessToken());

        // ★ 데모용 초간단 식별 쿠키: uid = users.id
        var builder = ResponseCookie.from("uid", String.valueOf(out.user().id()))
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite(crossSite ? "None" : "Lax")
                .secure(crossSite); // cross-site면 Secure 필수

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder = builder.domain(cookieDomain.trim());
        }

        var cookie = builder.build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(out);
    }

    /** 현재 로그인 사용자 조회 (쿠키 uid 기준) */
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

    /** 로그아웃: uid 쿠키 제거 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse res) {
        var builder = ResponseCookie.from("uid", "")
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite(crossSite ? "None" : "Lax")
                .secure(crossSite);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder = builder.domain(cookieDomain.trim());
        }

        var cookie = builder.build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.noContent().build();
    }
}
