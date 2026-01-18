package io.github.ssforu.pin4u.features.requests.application;

import java.util.List;
import java.util.Optional;

public interface AiSummaryService {

    // [Theme 2] 비동기 리스너가 호출할 메인 메서드 (저장까지 수행)
    void generateAndSaveSummary(String requestSlug);

    /**
     * evidence 정보를 바탕으로 요약을 시도합니다. 실패하면 Optional.empty().
     */
    Optional<String> generateSummary(
            String placeName,
            String categoryName,
            Double rating,
            Integer ratingCount,
            List<String> reviewSnippets,
            List<String> userTags
    );
}