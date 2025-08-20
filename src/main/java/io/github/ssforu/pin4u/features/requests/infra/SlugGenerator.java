package io.github.ssforu.pin4u.features.requests.infra;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class SlugGenerator {
    private static final char[] BASE62 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RND = new SecureRandom();

    // 기존 시그니처 유지. seed는 무시(호출부 호환성 보장)
    public String generate(String seed) {
        int len = 10 + RND.nextInt(3); // 10~12
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(BASE62[RND.nextInt(BASE62.length)]);
        return sb.toString();
    }
}
