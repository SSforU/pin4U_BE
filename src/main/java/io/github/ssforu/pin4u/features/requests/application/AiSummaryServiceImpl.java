package io.github.ssforu.pin4u.features.requests.application;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final WebClient openai;                // 오픈AI용 WebClient

    @Value("${app.ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${app.ai.openai.model:gpt-4o-mini}")
    private String model;

    private final ObjectMapper om = new ObjectMapper();

    // 🔧 명시적 생성자 + @Qualifier 로 충돌 해결
    public AiSummaryServiceImpl(@Qualifier("openaiWebClient") WebClient openai) {
        this.openai = openai;
    }

    @Override
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
            ev.put("place_name", placeName);          // 필수
            ev.put("category_name", categoryName);    // 필수
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
}
