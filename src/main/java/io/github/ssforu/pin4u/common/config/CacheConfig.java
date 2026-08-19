package io.github.ssforu.pin4u.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("stations");
        manager.setCaffeine(Caffeine.newBuilder()
                // 전국 지하철역 약 700개. 검색어 조합을 감안해 1000 엔트리.
                .maximumSize(1000)
                // 역 데이터는 거의 변경되지 않으므로 1시간 TTL로 충분.
                .expireAfterWrite(Duration.ofHours(1))
                .recordStats());
        return manager;
    }
}
