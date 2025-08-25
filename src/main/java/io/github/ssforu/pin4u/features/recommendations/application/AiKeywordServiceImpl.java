package io.github.ssforu.pin4u.features.recommendations.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiKeywordServiceImpl implements AiKeywordService {

    private final WebClient openai;

    @Value("${app.ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${app.ai.openai.model:gpt-4o-mini}")
    private String model;

    private final ObjectMapper om = new ObjectMapper();

    // 🔧 어느 WebClient 쓸지 명시!
    public AiKeywordServiceImpl(@Qualifier("openaiWebClient") WebClient openai) {
        this.openai = openai;
    }

    @Override
    public List<String> extractTop2(String message) {
        // 비어있으면 휴리스틱 폴백
        if (message == null || message.isBlank() || !aiEnabled) {
            return heuristicFallback(message);
        }
        try {
            String system = """
                너는 한국어 문장에서 '장소 검색용 키워드'를 최대 2개 짧게 추출하는 도우미야.
                규칙: (1) 1~2개, (2) 너무 일반적인 단어 제외(예: 장소, 추천), (3) 상호명이나 카테고리 단어 위주로,
                (4) 출력은 JSON {"keywords":["...","..."]} 만.
                """;
            String user = message.trim();

            Map<String,Object> body = Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "max_tokens", 60,
                    "messages", List.of(
                            Map.of("role","system","content", system),
                            Map.of("role","user","content", user)
                    )
            );

            Map resp = openai.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorReturn(null)
                    .block();

            List<String> out = parseKeywords(resp);
            if (out.isEmpty()) return heuristicFallback(message);
            return out;
        } catch (Exception e) {
            log.warn("[AI] keyword extract fail: {}", e.toString());
            return heuristicFallback(message);
        }
    }

    private List<String> parseKeywords(Map resp) {
        if (resp == null) return List.of();
        Object choicesObj = resp.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) return List.of();
        Object ch0 = choices.get(0);
        if (!(ch0 instanceof Map<?,?> ch0m)) return List.of();
        Object msg = ch0m.get("message");
        if (!(msg instanceof Map<?,?> m)) return List.of();
        Object content = m.get("content");
        if (content == null) return List.of();
        try {
            Map parsed = om.readValue(String.valueOf(content).trim(), Map.class);
            Object arr = parsed.get("keywords");
            if (arr instanceof List<?> list) {
                LinkedHashSet<String> dedup = new LinkedHashSet<>();
                for (Object o : list) {
                    String s = String.valueOf(o).trim();
                    if (!s.isEmpty()) dedup.add(s);
                    if (dedup.size() >= 2) break;
                }
                return new ArrayList<>(dedup);
            }
        } catch (Exception ignore) {}
        return List.of();
    }

    private List<String> heuristicFallback(String msg) {
        // 아주 단순 폴백(최대 2개)
        if (msg == null) return List.of("카페");
        String m = msg.trim();
        if (m.isEmpty()) return List.of("카페");

        String[] dictCafe = {"카페","커피","디저트","빵","베이커리"};
        for (String t : dictCafe) if (m.contains(t)) return List.of("카페");

        // 공백 단위로 2개 뽑기
        List<String> out = new ArrayList<>();
        for (String t : m.split("\\s+")) {
            t = t.trim();
            if (t.length() < 2 || t.length() > 15) continue;
            out.add(t);
            if (out.size() >= 2) break;
        }
        if (out.isEmpty()) out.add("카페");
        return out;
    }
}
