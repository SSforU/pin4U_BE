package io.github.ssforu.pin4u.features.auth.api;

import io.github.ssforu.pin4u.common.annotation.LoginUser;
import io.github.ssforu.pin4u.common.auth.AuthTokenProvider;
import io.github.ssforu.pin4u.features.auth.application.AuthService;
import io.github.ssforu.pin4u.features.auth.dto.AuthDtos;
import io.github.ssforu.pin4u.features.member.infra.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository users;
    private final AuthTokenProvider tokenProvider;

    @Value("${app.cookies.crossSite:true}")
    private boolean crossSite;

    @Value("${app.cookies.domain:}")
    private String cookieDomain;

    @Operation(
            summary = "카카오 로그인",
            description = "프론트에서 전달한 Kakao access_token을 검증하고 HMAC 서명된 uid 쿠키를 발급합니다."
    )
    @PostMapping("/kakao/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(@RequestBody AuthDtos.KakaoLoginRequest body,
                                                        HttpServletResponse res) {
        if (body == null || body.accessToken() == null || body.accessToken().isBlank()) {
            throw new IllegalArgumentException("access_token_required");
        }

        var out = authService.loginWithKakaoToken(body.accessToken());

        String signedToken = tokenProvider.issueToken(out.user().id());
        ResponseCookie cookie = buildUidCookie(signedToken, Duration.ofDays(30));
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(out);
    }

    @Operation(
            summary = "현재 로그인 사용자 조회",
            description = "서명된 uid 쿠키 기준으로 로그인 사용자를 반환합니다. 없으면 204.",
            security = @SecurityRequirement(name = "uidCookie")
    )
    @GetMapping("/me")
    public ResponseEntity<AuthDtos.LoginUser> me(@LoginUser(required = false) Long userId) {
        if (userId == null) return ResponseEntity.noContent().build();

        return users.findById(userId)
                .map(user -> ResponseEntity.ok(new AuthDtos.LoginUser(user.getId(), user.getNickname())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "로그아웃", description = "uid 쿠키를 만료시킵니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse res) {
        ResponseCookie cookie = buildUidCookie("", Duration.ZERO);
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie buildUidCookie(String value, Duration maxAge) {
        var builder = ResponseCookie.from("uid", value)
                .httpOnly(true)
                .path("/")
                .maxAge(maxAge)
                .sameSite(crossSite ? "None" : "Lax")
                .secure(crossSite);

        if (crossSite) {
            builder = builder.partitioned(true);
        }
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder = builder.domain(cookieDomain.trim());
        }
        return builder.build();
    }
}
