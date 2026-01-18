package io.github.ssforu.pin4u.common.resolver;

import io.github.ssforu.pin4u.common.annotation.LoginUser;
import io.github.ssforu.pin4u.features.member.domain.User;
import io.github.ssforu.pin4u.features.member.infra.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // @LoginUser 어노테이션이 붙어있고, 타입이 Long(ID) 이거나 User 객체인 경우 지원
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

        // 1. 쿠키에서 uid 추출
        String uid = extractUidFromCookies(request);

        if (uid == null) {
            if (annotation != null && annotation.required()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login_required");
            }
            return null;
        }

        try {
            Long userId = Long.valueOf(uid);

            // 파라미터 타입이 Long이면 바로 ID 반환 (DB 조회 X -> 성능 이점)
            if (Long.class.isAssignableFrom(parameter.getParameterType())) {
                return userId;
            }

            // 파라미터 타입이 User면 DB 조회 후 객체 반환
            if (User.class.isAssignableFrom(parameter.getParameterType())) {
                return userRepository.findById(userId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user_not_found"));
            }

        } catch (NumberFormatException e) {
            if (annotation != null && annotation.required()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid_uid");
            }
        }

        return null;
    }

    private String extractUidFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "uid".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}