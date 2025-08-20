package io.github.ssforu.pin4u.common.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            try {
                flyway.repair(); // 기존 DB의 flyway_schema_history 체크섬만 업데이트
            } catch (Exception ignore) {
                // 스키마 히스토리가 없거나 이미 정상이면 무시하고 바로 migrate
            }
            flyway.migrate();
        };
    }
}
