package io.github.ssforu.pin4u.common.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Component
public class GidCookieFilter implements Filter {
    public static final String GID = "gid";

    @Value("${app.cookies.crossSite:false}")
    private boolean crossSite;

    @Value("${app.cookies.domain:}")
    private String cookieDomain; // 예: ".ss4u-pin4u.store" (비어있으면 Host-Only)

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest r = (HttpServletRequest) req;
        HttpServletResponse w = (HttpServletResponse) res;

        Cookie exists = WebUtils.getCookie(r, GID);
        if (exists == null || exists.getValue() == null || exists.getValue().isBlank()) {
            String gid = UUID.randomUUID().toString();

            boolean httpsByServlet = r.isSecure();
            boolean httpsByHeader = "https".equalsIgnoreCase(safeHeader(r, "X-Forwarded-Proto"));
            boolean isHttps = httpsByServlet || httpsByHeader;

            // 교차 출처 쿠키는 SameSite=None; Secure 필수
            String sameSite = crossSite ? "None" : "Lax";
            boolean secure = crossSite || isHttps;

            StringBuilder sb = new StringBuilder();
            sb.append(GID).append("=").append(gid)
                    .append("; Max-Age=").append(Duration.ofDays(365).toSeconds())
                    .append("; Path=/; HttpOnly")
                    .append("; SameSite=").append(sameSite);
            if (secure) sb.append("; Secure");
            if (cookieDomain != null && !cookieDomain.isBlank()) {
                sb.append("; Domain=").append(cookieDomain.trim());
            }

            w.addHeader(HttpHeaders.SET_COOKIE, sb.toString());
        }

        chain.doFilter(req, res);
    }

    private static String safeHeader(HttpServletRequest r, String name) {
        String v = r.getHeader(name);
        return v == null ? "" : v.toLowerCase(Locale.ROOT).trim();
    }
}
