package io.github.ssforu.pin4u.common.resolver;

import io.github.ssforu.pin4u.common.annotation.LoginUser;
import io.github.ssforu.pin4u.common.auth.AuthTokenProvider;
import io.github.ssforu.pin4u.features.member.domain.User;
import io.github.ssforu.pin4u.features.member.infra.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;
    private final AuthTokenProvider tokenProvider;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean hasAnnotation = parameter.hasParameterAnnotation(LoginUser.class);
        boolean isLongType = Long.class.isAssignableFrom(parameter.getParameterType());
        boolean isUserType = User.class.isAssignableFrom(parameter.getParameterType());
        return hasAnnotation && (isLongType || isUserType);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {

        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        LoginUser annotation = parameter.getParameterAnnotation(LoginUser.class);

        Optional<Long> verified = extractVerifiedUid(request);

        if (verified.isEmpty()) {
            if (annotation != null && annotation.required()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login_required");
            }
            return null;
        }

        Long userId = verified.get();

        if (Long.class.isAssignableFrom(parameter.getParameterType())) {
            return userId;
        }

        if (User.class.isAssignableFrom(parameter.getParameterType())) {
            return userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user_not_found"));
        }

        return null;
    }

    private Optional<Long> extractVerifiedUid(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> "uid".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .flatMap(tokenProvider::validateToken);
    }
}
