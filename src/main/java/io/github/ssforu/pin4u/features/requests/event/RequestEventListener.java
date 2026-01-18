package io.github.ssforu.pin4u.features.requests.event;

import io.github.ssforu.pin4u.features.requests.application.AiSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestEventListener {

    private final AiSummaryService aiSummaryService;

    /**
     * @Async("aiTaskExecutor"): 별도의 스레드 풀에서 실행 (Non-blocking)
     * @TransactionalEventListener(phase = AFTER_COMMIT):
     * 메인 트랜잭션(요청 저장)이 DB에 완전히 커밋된 후에 실행함.
     * (데이터가 없어서 AI가 조회 실패하는 동시성 문제 방지)
     */
    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRequestCreated(RequestCreatedEvent event) {
        log.info("🚀 [Async] AI Summary Event Received: slug={}", event.requestSlug());

        try {
            // 오래 걸리는 작업 (AI 호출 + DB 업데이트)
            aiSummaryService.generateAndSaveSummary(event.requestSlug());
            log.info("✅ [Async] AI Summary Completed: slug={}", event.requestSlug());
        } catch (Exception e) {
            log.error("❌ [Async] AI Summary Failed: slug={}, error={}", event.requestSlug(), e.getMessage());
            // 추후 여기에 '실패 알림' 로직 추가 가능
        }
    }
}