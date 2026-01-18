package io.github.ssforu.pin4u.common.config;

import io.github.ssforu.pin4u.common.resolver.LoginUserArgumentResolver;
import org.apache.catalina.filters.RateLimitFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver; // 추가

    public WebConfig(LoginUserArgumentResolver loginUserArgumentResolver) { // 생성자 주입 추가
        this.loginUserArgumentResolver = loginUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 프리뷰까지 안정적으로 허용하기 위해 allowedOriginPatterns 사용
                .allowedOriginPatterns(
                        // 로컬
                        "http://localhost:5173",
                        "https://localhost:5173",
                        "http://localhost:5175",
                        "https://localhost:5175",
                        // Vercel 프로덕션
                        "https://pin4-u-fe.vercel.app",
                        // Vercel 프리뷰(브랜치/PR)
                        "https://pin4-u-fe-*.vercel.app",
                        // 서비스 커스텀 도메인(웹앱)
                        "https://ss4u-pin4u.store",
                        "https://www.ss4u-pin4u.store"
                        // ※ API 자신(https://api.ss4u-...)은 요청 Origin이 아니므로 보통 불필요
                )
                .allowedMethods("GET","POST","PUT","DELETE","PATCH","OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
        // 필요 시: .exposedHeaders("X-Request-Id");
    }

    /** GID 쿠키 필터를 가장 먼저 실행 */
    @Bean
    FilterRegistrationBean<GidCookieFilter> gidFilter(GidCookieFilter filter) {
        FilterRegistrationBean<GidCookieFilter> b = new FilterRegistrationBean<>(filter);
        b.setOrder(1);
        b.addUrlPatterns("/*");
        return b;
    }

    /** (옵션) 레이트리밋: 필요한 환경에서만 ON */
    @Bean
    @ConditionalOnClass(RateLimitFilter.class)
    @ConditionalOnProperty(prefix = "app.ratelimit", name = "enabled", havingValue = "true")
    FilterRegistrationBean<RateLimitFilter> rlFilter() {
        RateLimitFilter f = new RateLimitFilter();
        FilterRegistrationBean<RateLimitFilter> b = new FilterRegistrationBean<>(f);
        b.setOrder(2);
        b.addUrlPatterns("/*");
        return b;
    }
}
