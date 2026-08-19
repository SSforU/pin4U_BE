package io.github.ssforu.pin4u.features.requests.event;

import io.github.ssforu.pin4u.features.requests.application.AiSummaryService;
import io.github.ssforu.pin4u.features.requests.domain.AiSummaryJob;
import io.github.ssforu.pin4u.features.requests.infra.AiSummaryJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestEventListener {

    private final AiSummaryService aiSummaryService;
    private final AiSummaryJobRepository jobRepository;

    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRequestCreated(RequestCreatedEvent event) {
        String slug = event.requestSlug();
        log.info("[Async] AI Summary Event Received: slug={}", slug);

        // UNIQUE 제약(request_slug)으로 중복 실행 방지
        AiSummaryJob job;
        try {
            job = jobRepository.save(new AiSummaryJob(slug));
        } catch (DataIntegrityViolationException e) {
            log.info("[Async] Job already exists for slug={}, skipping", slug);
            return;
        }

        try {
            job.setStatus(AiSummaryJob.Status.RUNNING);
            job.setAttempts(job.getAttempts() + 1);
            jobRepository.save(job);

            aiSummaryService.generateAndSaveSummary(slug);

            job.setStatus(AiSummaryJob.Status.SUCCEEDED);
            jobRepository.save(job);
            log.info("[Async] AI Summary Completed: slug={}", slug);

        } catch (Exception e) {
            log.error("[Async] AI Summary Failed: slug={}, error={}", slug, e.getMessage());
            job.setStatus(AiSummaryJob.Status.FAILED);
            job.setLastError(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "unknown");
            try {
                jobRepository.save(job);
            } catch (Exception saveErr) {
                log.error("[Async] Failed to save job failure status: {}", saveErr.getMessage());
            }
        }
    }
}
