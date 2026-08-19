package io.github.ssforu.pin4u.features.requests.application;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiSummaryServiceImpl.generateAndSaveSummary에 @Transactional이 없음을 검증.
 * 외부 API 호출이 트랜잭션 밖에서 실행되어야 커넥션 풀 고갈을 방지한다.
 * 트랜잭션은 AiSummaryTxHelper의 loadTargets(readOnly)/saveSummary(write)에 위임.
 */
class AiSummaryServiceTransactionTest {

    @Test
    void generateAndSaveSummary_hasNoTransactionalAnnotation() throws Exception {
        Method method = AiSummaryServiceImpl.class.getMethod("generateAndSaveSummary", String.class);
        Transactional txAnnotation = method.getAnnotation(Transactional.class);

        assertThat(txAnnotation)
                .as("generateAndSaveSummary must NOT have @Transactional — " +
                        "external API calls must run outside transaction to prevent connection pool exhaustion")
                .isNull();
    }

    @Test
    void txHelper_loadTargets_isReadOnly() throws Exception {
        Method method = AiSummaryTxHelper.class.getMethod("loadTargets", String.class);
        Transactional tx = method.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isTrue();
    }

    @Test
    void txHelper_saveSummary_isWriteTransaction() throws Exception {
        Method method = AiSummaryTxHelper.class.getMethod("saveSummary", Long.class, String.class);
        Transactional tx = method.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isFalse();
    }
}
