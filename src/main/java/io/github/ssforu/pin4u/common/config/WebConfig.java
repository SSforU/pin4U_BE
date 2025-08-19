package io.github.ssforu.pin4u.common.config;

import org.apache.catalina.filters.RateLimitFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// ⚠️ GidCookieFilter 가 @Component 또는 @Bean 으로 등록되어 있어야 합니다.
// 만약 @Component 가 아니라면 아래에 @Bean 으로 직접 등록하세요.
// @Bean
// public GidCookieFilter gidCookieFilter() { return new GidCookieFilter(); }

@Configuration
public class WebConfig {

    @Bean
    FilterRegistrationBean<GidCookieFilter> gidFilter(GidCookieFilter f){
        FilterRegistrationBean<GidCookieFilter> b = new FilterRegistrationBean<>(f);
        b.setOrder(1); // 먼저 gid 부여
        b.addUrlPatterns("/*");
        return b;
    }

    // ✅ 로컬 기본 OFF. 필요한 프로파일/환경에서만 ON.
    @Bean
    @ConditionalOnClass(RateLimitFilter.class)
    @ConditionalOnProperty(prefix = "app.ratelimit", name = "enabled", havingValue = "true")
    FilterRegistrationBean<RateLimitFilter> rlFilter(){
        RateLimitFilter f = new RateLimitFilter(); // 주입받지 않고 직접 생성
        FilterRegistrationBean<RateLimitFilter> b = new FilterRegistrationBean<>(f);
        b.setOrder(2); // 이후 레이트리밋
        b.addUrlPatterns("/*");
        // 필요하면 init-param 설정 (톰캣 RateLimitFilter 문서 기준 키를 정확히 사용하세요)
        // b.addInitParameter("requests", "100");
        // b.addInitParameter("window", "PT1M"); // ISO-8601 duration 예시
        return b;
    }

    // WebConfig 에 추가 (이미 @Component면 불필요) - 0단계 헷갈리는 거에서 추가
    @Bean
    public GidCookieFilter gidCookieFilter() {
        return new GidCookieFilter();
    }

}
