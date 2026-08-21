package io.github.ssforu.pin4u.common.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 기반 인증 토큰 발급·검증.
 * 토큰 포맷: {uid}.{expiresEpochSeconds}.{hmacHex}
 */
@Component
public class AuthTokenProvider {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Duration DEFAULT_TTL = Duration.ofDays(30);

    private final byte[] secretBytes;

    public AuthTokenProvider(@Value("${app.auth.hmac-secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "app.auth.hmac-secret must be at least 32 characters. "
                            + "Generate with: openssl rand -hex 32");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issueToken(Long uid) {
        long expires = Instant.now().plus(DEFAULT_TTL).getEpochSecond();
        String payload = uid + "." + expires;
        return payload + "." + sign(payload);
    }

    public Optional<Long> validateToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        String[] parts = token.split("\\.", 3);
        if (parts.length != 3) return Optional.empty();

        try {
            long uid = Long.parseLong(parts[0]);
            long expires = Long.parseLong(parts[1]);

            if (Instant.now().getEpochSecond() > expires) return Optional.empty();

            String expected = sign(parts[0] + "." + parts[1]);
            if (!constantTimeEquals(expected, parts[2])) return Optional.empty();

            return Optional.of(uid);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, ALGORITHM));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
