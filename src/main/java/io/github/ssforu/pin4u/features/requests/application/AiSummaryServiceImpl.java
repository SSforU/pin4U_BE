package io.github.ssforu.pin4u.features.requests.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AiSummaryServiceImpl implements AiSummaryService {

    private final WebClient openai;
    private final AiSummaryTxHelper txHelper;

    @Value("${app.ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${app.ai.openai.model:gpt-4o-mini}")
    private String model;

    private final ObjectMapper om = new ObjectMapper();

    public AiSummaryServiceImpl(
            @Qualifier("openaiWebClient") WebClient openai,
            AiSummaryTxHelper txHelper
    ) {
        this.openai = openai;
        this.txHelper = txHelper;
    }

    /**
     * 트랜잭션 3단 분리:
     * [1단: 짧은 읽기 tx] txHelper.loadTargets — 대상 조회, DB 커넥션 즉시 반환
     * [2단: tx 없음] generateSummary — OpenAI blocking 호출, DB 커넥션 미점유
     * [3단: 짧은 쓰기 tx] txHelper.saveSummary — 결과 저장, DB 커넥션 즉시 반환
     *
     * 이전 구조: 단일 @Transactional 안에서 OpenAI 최대 20초 blocking →
     * aiTaskExecutor max 50 × DB 커넥션 점유 vs Hikari pool 30 → 풀 고갈.
     * 분리 후: 외부 호출 동안 DB 커넥션을 점유하지 않는다.
     */
    @Override
    public void generateAndSaveSummary(String requestSlug) {
        // [1단: 읽기 tx] 대상 장소 조회 — tx 종료 후 커넥션 반환
        var targets = txHelper.loadTargets(requestSlug);

        for (var target : targets) {
            // [2단: tx 없음] 외부 API 호출 — DB 커넥션 미점유
            Optional<String> summaryOpt = generateSummary(
                    target.placeName(), target.categoryName(),
                    null, null, null, null
            );

            // [3단: 쓰기 tx] 결과 저장 — 짧은 tx
            if (summaryOpt.isPresent()) {
                txHelper.saveSummary(target.placeId(), summaryOpt.get());
                log.info("[AI] Saved summary for place: {}", target.placeName());
            }
        }
        log.info("[AI] Completed summary generation for request: {}", requestSlug);
    }

    @Override
    @Bulkhead(name = "openai")
    @CircuitBreaker(name = "openai", fallbackMethod = "generateSummaryFallback")
    @Retry(name = "openai")
    public Optional<String> generateSummary(
            String placeName, String categoryName,
            Double rating, Integer ratingCount,
            List<String> reviewSnippets, List<String> userTags
    ) {
        if (!aiEnabled) return Optional.empty();
        try {
            Map<String, Object> ev = new java.util.LinkedHashMap<>();
            ev.put("place_name", placeName);
            ev.put("category_name", categoryName);
            if (rating != null) ev.put("rating", rating);
            if (ratingCount != null) ev.put("rating_count", ratingCount);
            if (reviewSnippets != null && !reviewSnippets.isEmpty()) ev.put("review_snippets", reviewSnippets);
            if (userTags != null && !userTags.isEmpty()) ev.put("user_tags", userTags);

            String system = """
                당신은 사용자의 취향을 고려해 장소를 '한 줄'로 요약하는 한국어 어시스턴트입니다.
                규칙: (1) 60자 이내, (2) 과장/추측 금지, (3) 제공된 evidence만 사용, (4) 매장명 언급 금지, (5) 존칭/감탄사 금지.
                출력은 JSON {"summary_text": "..."} 형태만 반환하세요.
                """;

            String user = om.writeValueAsString(Map.of("evidence", ev));

            Map<String, Object> body = Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "max_tokens", 120,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    )
            );

            Map resp = openai.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(e -> {
                        log.warn("[AI] openai call failed: {}", e.toString());
                        return Mono.empty();
                    })
                    .blockOptional()
                    .orElse(null);

            if (resp == null) return Optional.empty();
            Object choicesObj = resp.get("choices");
            if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) return Optional.empty();
            Object ch0 = choices.get(0);
            if (!(ch0 instanceof Map<?, ?> ch0Map)) return Optional.empty();
            Object msgObj = ch0Map.get("message");
            if (!(msgObj instanceof Map<?, ?> msgMap)) return Optional.empty();
            Object content = msgMap.get("content");
            if (content == null) return Optional.empty();
            String contentStr = String.valueOf(content).trim();
            if (contentStr.isEmpty()) return Optional.empty();

            try {
                Map parsed = om.readValue(contentStr, Map.class);
                Object st = parsed.get("summary_text");
                if (st != null && !String.valueOf(st).isBlank()) {
                    return Optional.of(String.valueOf(st));
                }
            } catch (Exception ignore) { /* plain text fallback */ }

            return Optional.of(contentStr);
        } catch (Exception e) {
            log.warn("[AI] summary fail: {}", e.toString());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unused")
    private Optional<String> generateSummaryFallback(
            String placeName, String categoryName,
            Double rating, Integer ratingCount,
            List<String> reviewSnippets, List<String> userTags,
            Throwable t) {
        log.warn("[AI] summary circuit open for place='{}': {}", placeName, t.getMessage());
        return Optional.empty();
    }
}
