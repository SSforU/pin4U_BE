package io.github.ssforu.pin4u.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.ssforu.pin4u.TestcontainersConfiguration;
import io.github.ssforu.pin4u.features.recommendations.application.AiKeywordService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Tag("integration")
@SpringBootTest(properties = {
    "app.ai.enabled=true",
    "resilience4j.circuitbreaker.instances.openai.sliding-window-size=5",
    "resilience4j.circuitbreaker.instances.openai.minimum-number-of-calls=5",
    "resilience4j.circuitbreaker.instances.openai.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.openai.wait-duration-in-open-state=60s"
})
@Import(TestcontainersConfiguration.class)
class CircuitBreakerStateTransitionTest {

    @Autowired
    private CircuitBreakerRegistry registry;

    @Autowired
    private AiKeywordService aiKeywordService;

    @BeforeEach
    void resetCircuitBreaker() {
        CircuitBreaker cb = registry.circuitBreaker("openai");
        cb.reset();
    }

    @Test
    @DisplayName("openai CB: 연속 실패 시 CLOSED → OPEN 전이")
    void openai_closedToOpen() {
        CircuitBreaker cb = registry.circuitBreaker("openai");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        for (int i = 0; i < 6; i++) {
            try {
                CircuitBreaker.decorateRunnable(cb, () -> {
                    throw new RuntimeException("simulated failure");
                }).run();
            } catch (Exception ignored) {
            }
        }

        assertThat(cb.getState())
                .as("5건 이상 연속 실패 후 OPEN 상태여야 한다")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("openai CB: OPEN 상태에서 호출 시 즉시 차단 + fallback 반환")
    void openai_openState_immediatelyFallsBack() {
        CircuitBreaker cb = registry.circuitBreaker("openai");
        cb.transitionToOpenState();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        List<String> result = aiKeywordService.extractTop2("강남 카페 추천");
        assertThat(result).as("OPEN 상태에서 fallback이 반환되어야 한다").isNotNull();

        assertThat(cb.getMetrics().getNumberOfNotPermittedCalls())
                .as("OPEN 상태에서 차단된 호출이 1건 이상이어야 한다")
                .isGreaterThanOrEqualTo(1);
    }
}
