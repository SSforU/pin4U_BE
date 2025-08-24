package io.github.ssforu.pin4u.features.requests.application;

import java.util.List;
import java.util.Optional;

public interface AiSummaryService {
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
