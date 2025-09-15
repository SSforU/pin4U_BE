package io.github.ssforu.pin4u.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Seed seed = new Seed();
    private final Kakao kakao = new Kakao();
    private final Og og = new Og();
    private final Summary summary = new Summary();

    // 코드에서 기대하는 메서드 이름 그대로 노출
    public Seed seed() { return seed; }
    public Kakao kakao() { return kakao; }
    public Og og() { return og; }
    public Summary summary() { return summary; }

    public static class Seed {
        private boolean enabled = false;
        public boolean enabled() { return enabled; }      // 기대: seed().enabled()
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    public static class Kakao {
        private boolean enabled = false;
        public boolean enabled() { return enabled; }      // 기대: kakao().enabled()
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    public static class Og {
        private boolean imageEnabled = false;
        public boolean imageEnabled() { return imageEnabled; } // 기대: og().imageEnabled()
        public void setImageEnabled(boolean imageEnabled) { this.imageEnabled = imageEnabled; }
    }
    public static class Summary {
        private int ttlDays = 7;
        public int ttlDays() { return ttlDays; }          // 기대: summary().ttlDays()
        public void setTtlDays(int ttlDays) { this.ttlDays = ttlDays; }
    }
}
