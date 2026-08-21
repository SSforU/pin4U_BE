package io.github.ssforu.pin4u.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthTokenProviderTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";
    private AuthTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AuthTokenProvider(SECRET);
    }

    @Test
    void validToken_returnsUid() {
        String token = provider.issueToken(42L);
        Optional<Long> result = provider.validateToken(token);
        assertThat(result).contains(42L);
    }

    @Test
    void tamperedUid_rejected() {
        String token = provider.issueToken(42L);
        String tampered = "99" + token.substring(2);
        assertThat(provider.validateToken(tampered)).isEmpty();
    }

    @Test
    void tamperedSignature_rejected() {
        String token = provider.issueToken(42L);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "0000000000";
        assertThat(provider.validateToken(tampered)).isEmpty();
    }

    @Test
    void plainUid_rejected() {
        assertThat(provider.validateToken("1")).isEmpty();
        assertThat(provider.validateToken("42")).isEmpty();
    }

    @Test
    void nullOrBlank_rejected() {
        assertThat(provider.validateToken(null)).isEmpty();
        assertThat(provider.validateToken("")).isEmpty();
        assertThat(provider.validateToken("   ")).isEmpty();
    }

    @Test
    void garbageInput_rejected() {
        assertThat(provider.validateToken("garbage")).isEmpty();
        assertThat(provider.validateToken("a.b.c")).isEmpty();
        assertThat(provider.validateToken("1.abc.def")).isEmpty();
    }

    @Test
    void shortSecret_throwsException() {
        assertThatThrownBy(() -> new AuthTokenProvider("short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    void differentSecrets_produceDifferentTokens() {
        AuthTokenProvider other = new AuthTokenProvider("another-secret-that-is-at-least-32-chars!!");
        String token = provider.issueToken(42L);
        assertThat(other.validateToken(token)).isEmpty();
    }
}
