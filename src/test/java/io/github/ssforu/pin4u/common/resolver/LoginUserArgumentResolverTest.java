package io.github.ssforu.pin4u.common.resolver;

import io.github.ssforu.pin4u.common.annotation.LoginUser;
import io.github.ssforu.pin4u.common.auth.AuthTokenProvider;
import io.github.ssforu.pin4u.features.member.infra.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class LoginUserArgumentResolverTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";
    private AuthTokenProvider tokenProvider;
    private LoginUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        tokenProvider = new AuthTokenProvider(SECRET);
        UserRepository userRepo = mock(UserRepository.class);
        resolver = new LoginUserArgumentResolver(userRepo, tokenProvider);
    }

    // 테스트용 메서드 시그니처
    void requiredEndpoint(@LoginUser(required = true) Long userId) {}
    void optionalEndpoint(@LoginUser(required = false) Long userId) {}

    private MethodParameter paramOf(String methodName) throws Exception {
        Method m = getClass().getDeclaredMethod(methodName, Long.class);
        return new MethodParameter(m, 0);
    }

    @Test
    void validSignedToken_returnsUid() throws Exception {
        String token = tokenProvider.issueToken(42L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("uid", token));
        NativeWebRequest webRequest = new ServletWebRequest(request);

        Object result = resolver.resolveArgument(paramOf("requiredEndpoint"), null, webRequest, null);

        assertThat(result).isEqualTo(42L);
    }

    @Test
    void forgedPlainUid_required_returns401() throws Exception {
        // 평문 uid=1 쿠키 위조 → 401
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("uid", "1"));
        NativeWebRequest webRequest = new ServletWebRequest(request);

        assertThatThrownBy(() ->
                resolver.resolveArgument(paramOf("requiredEndpoint"), null, webRequest, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("login_required");
    }

    @Test
    void tamperedSignature_required_returns401() throws Exception {
        String token = tokenProvider.issueToken(42L);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "0000000000";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("uid", tampered));
        NativeWebRequest webRequest = new ServletWebRequest(request);

        assertThatThrownBy(() ->
                resolver.resolveArgument(paramOf("requiredEndpoint"), null, webRequest, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("login_required");
    }

    @Test
    void differentSecretToken_rejected() throws Exception {
        AuthTokenProvider otherProvider = new AuthTokenProvider("another-secret-that-is-at-least-32-chars!!");
        String otherToken = otherProvider.issueToken(42L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("uid", otherToken));
        NativeWebRequest webRequest = new ServletWebRequest(request);

        assertThatThrownBy(() ->
                resolver.resolveArgument(paramOf("requiredEndpoint"), null, webRequest, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("login_required");
    }

    @Test
    void noCookie_required_returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        NativeWebRequest webRequest = new ServletWebRequest(request);

        assertThatThrownBy(() ->
                resolver.resolveArgument(paramOf("requiredEndpoint"), null, webRequest, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("login_required");
    }

    @Test
    void noCookie_optional_returnsNull() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        NativeWebRequest webRequest = new ServletWebRequest(request);

        Object result = resolver.resolveArgument(paramOf("optionalEndpoint"), null, webRequest, null);

        assertThat(result).isNull();
    }

    @Test
    void forgedPlainUid_optional_returnsNull() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("uid", "99"));
        NativeWebRequest webRequest = new ServletWebRequest(request);

        Object result = resolver.resolveArgument(paramOf("optionalEndpoint"), null, webRequest, null);

        assertThat(result).isNull();
    }
}
