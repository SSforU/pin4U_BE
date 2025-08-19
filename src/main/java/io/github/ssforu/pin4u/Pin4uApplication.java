package io.github.ssforu.pin4u;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "io.github.ssforu.pin4u")
@ComponentScan(
        basePackages = "io.github.ssforu.pin4u",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "io\\.github\\.ssforu\\.pin4u\\.features\\.requests\\..*"
        )
)
@EnableJpaRepositories(
        basePackages = "io.github.ssforu.pin4u",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "io\\.github\\.ssforu\\.pin4u\\.features\\.requests\\..*"
        )
)
@EntityScan(basePackages = "io.github.ssforu.pin4u")
public class Pin4uApplication {
    public static void main(String[] args) {
        SpringApplication.run(Pin4uApplication.class, args);
    }
}
