package io.github.ssforu.pin4u;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "io.github.ssforu.pin4u")
@EnableJpaRepositories(basePackages = "io.github.ssforu.pin4u")

@EntityScan(basePackages = "io.github.ssforu.pin4u")
public class Pin4uApplication {
    public static void main(String[] args) {
        SpringApplication.run(Pin4uApplication.class, args);
    }
}
