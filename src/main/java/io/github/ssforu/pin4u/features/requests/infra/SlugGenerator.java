// src/main/java/io/github/ssforu/pin4u/features/requests/infra/SlugGenerator.java
package io.github.ssforu.pin4u.features.requests.infra;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class SlugGenerator {
    public String generate(String seed) {
        String base = (seed == null || seed.isBlank()) ? "req" : seed;
        String ts = OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return base + "-" + ts + "-" + suffix;
    }
}
