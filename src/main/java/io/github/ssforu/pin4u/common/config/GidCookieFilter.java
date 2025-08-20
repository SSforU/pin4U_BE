package io.github.ssforu.pin4u.common.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

//webconfig에서 FilterRegistrationBean 으로 순서 지정하기
@Component
public class GidCookieFilter implements Filter {
    public static final String GID = "gid";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest r = (HttpServletRequest) req;
        HttpServletResponse w = (HttpServletResponse) res;

        Cookie exists = WebUtils.getCookie(r, GID);
        if (exists == null || exists.getValue() == null || exists.getValue().isBlank()) {
            String gid = UUID.randomUUID().toString(); // UUIDv4
            boolean isHttps = r.isSecure(); // 배포는 HTTPS가 기본
            String cookie = String.format(
                    "%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax%s",
                    GID, gid, Duration.ofDays(365).toSeconds(),
                    isHttps ? "; Secure" : ""
            );
            w.addHeader(HttpHeaders.SET_COOKIE, cookie);
        }
        chain.doFilter(req, res);
    }
}
