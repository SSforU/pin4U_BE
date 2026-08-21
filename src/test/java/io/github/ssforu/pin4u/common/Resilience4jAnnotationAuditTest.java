package io.github.ssforu.pin4u.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.ssforu.pin4u.features.places.infra.KakaoSearchAdapterImpl;
import io.github.ssforu.pin4u.features.recommendations.application.AiKeywordServiceImpl;
import io.github.ssforu.pin4u.features.requests.application.AiSummaryServiceImpl;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Resilience4j 애노테이션의 fallback 메서드 시그니처가 올바른지 검증.
 * 원본 메서드 파라미터 + Throwable이 마지막이어야 런타임에 정상 동작.
 */
class Resilience4jAnnotationAuditTest {

    @Test
    void kakaoSearchFallback_signatureMatchesOriginal() {
        assertFallbackSignature(KakaoSearchAdapterImpl.class, "keywordSearch", "keywordSearchFallback");
    }

    @Test
    void aiKeywordFallback_signatureMatchesOriginal() {
        assertFallbackSignature(AiKeywordServiceImpl.class, "extractTop2", "extractTop2Fallback");
    }

    @Test
    void aiSummaryFallback_signatureMatchesOriginal() {
        assertFallbackSignature(AiSummaryServiceImpl.class, "generateSummary", "generateSummaryFallback");
    }

    private void assertFallbackSignature(Class<?> clazz, String originalName, String fallbackName) {
        Method original = Arrays.stream(clazz.getMethods())
                .filter(m -> m.getName().equals(originalName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Original method not found: " + originalName));

        // @CircuitBreaker에 fallbackMethod가 선언되어 있는지
        CircuitBreaker cbAnnotation = original.getAnnotation(CircuitBreaker.class);
        assertThat(cbAnnotation).isNotNull();
        assertThat(cbAnnotation.fallbackMethod()).isEqualTo(fallbackName);

        // fallback 메서드가 존재하고, 마지막 파라미터가 Throwable인지
        Method fallback = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getName().equals(fallbackName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Fallback method not found: " + fallbackName));

        Class<?>[] fbParams = fallback.getParameterTypes();
        assertThat(fbParams.length).isGreaterThan(0);
        assertThat(fbParams[fbParams.length - 1])
                .as("Fallback's last param must be Throwable")
                .isEqualTo(Throwable.class);

        // 원본 파라미터 + Throwable = fallback 파라미터
        Class<?>[] origParams = original.getParameterTypes();
        assertThat(fbParams.length).isEqualTo(origParams.length + 1);
    }
}
