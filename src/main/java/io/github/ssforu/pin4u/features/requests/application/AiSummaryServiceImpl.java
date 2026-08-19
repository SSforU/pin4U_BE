package io.github.ssforu.pin4u.features.requests.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.ssforu.pin4u.features.places.domain.Place;
import io.github.ssforu.pin4u.features.places.domain.PlaceSummary;
import io.github.ssforu.pin4u.features.places.infra.PlaceRepository;
import io.github.ssforu.pin4u.features.places.infra.PlaceSummaryRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceAggregateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AiSummaryServiceImpl implements AiSummaryService {

    private final WebClient openai;
    private final RequestPlaceAggregateRepository rpaRepository;
    private final PlaceRepository placeRepository;
    private final PlaceSummaryRepository placeSummaryRepository;

    @Value("${app.ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${app.ai.openai.model:gpt-4o-mini}")
    private String model;

    private final ObjectMapper om = new ObjectMapper();

    public AiSummaryServiceImpl(
            @Qualifier("openaiWebClient") WebClient openai,
            RequestPlaceAggregateRepository rpaRepository,
            PlaceRepository placeRepository,
            PlaceSummaryRepository placeSummaryRepository
    ) {
        this.openai = openai;
        this.rpaRepository = rpaRepository;
        this.placeRepository = placeRepository;
        this.placeSummaryRepository = placeSummaryRepository;
    }

    /**
     * [Theme 2] 비동기 처리 대상 메서드
     * 1. 지연 시뮬레이션 (3초)
     * 2. 요청에 속한 장소들 조회
     * 3. 각 장소에 대해 AI 요약 생성 및 저장
     */
    @Override
    @Transactional
    public void generateAndSaveSummary(String requestSlug) {
        // 요청에 포함된 장소들 조회
        var aggregates = rpaRepository.findAllByRequestId(requestSlug);

        for (var agg : aggregates) {
            Long placeId = agg.getPlaceId();

            // 이미 요약이 있으면 스킵 (비용 절감)
            if (placeSummaryRepository.existsById(placeId)) {
                continue;
            }

            // 장소 정보 조회
            Optional<Place> placeOpt = placeRepository.findById(placeId);
            if (placeOpt.isEmpty()) continue;
            Place place = placeOpt.get();

            // 3. 요약 생성 (OpenAI 호출)
            // (실제 데이터가 부족하므로 이름과 카테고리만으로 생성 시도)
            Optional<String> summaryOpt = generateSummary(
                    place.getPlaceName(),
                    place.getCategoryName(),
                    null, null, null, null // 상세 정보는 Mock이나 실제 수집 데이터 연동 필요
            );

            // 4. 저장
            if (summaryOpt.isPresent()) {
                PlaceSummary summary = PlaceSummary.builder()
                        .place(place)
                        .summaryText(summaryOpt.get())
                        .evidence("AI Generated based on basic info")
                        .build();
                placeSummaryRepository.save(summary);
                log.info("✅ [AI] Saved summary for place: {}", place.getPlaceName());
            }
        }
        log.info("🎉 [AI] Completed summary generation for request: {}", requestSlug);
    }

    // Bulkhead: Hikari pool(30) 대비 AI 동시 호출을 10으로 제한해 커넥션 풀 고갈 방지.
    // Retry가 안쪽에서 재시도 → 실패가 CircuitBreaker에 기록 → Bulkhead가 동시 호출 상한 관리.
    @Override
    @Bulkhead(name = "openai")
    @CircuitBreaker(name = "openai", fallbackMethod = "generateSummaryFallback")
    @Retry(name = "openai")
    public Optional<String> generateSummary(
            String placeName,
            String categoryName,
            Double rating,
            Integer ratingCount,
            List<String> reviewSnippets,
            List<String> userTags
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
            if (!(ch0 instanceof Map<?,?> ch0Map)) return Optional.empty();
            Object msgObj = ch0Map.get("message");
            if (!(msgObj instanceof Map<?,?> msgMap)) return Optional.empty();
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
            } catch (Exception ignore) { /* 평문 fallback */ }

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