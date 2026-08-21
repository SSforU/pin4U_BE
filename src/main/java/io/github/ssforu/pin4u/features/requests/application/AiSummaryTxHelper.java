package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.features.places.domain.Place;
import io.github.ssforu.pin4u.features.places.domain.PlaceSummary;
import io.github.ssforu.pin4u.features.places.infra.PlaceRepository;
import io.github.ssforu.pin4u.features.places.infra.PlaceSummaryRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceAggregateRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AiSummaryServiceImpl의 트랜잭션 경계를 분리하기 위한 헬퍼.
 * self-invocation으로 @Transactional이 무효화되는 것을 방지하기 위해 별도 빈으로 분리.
 *
 * generateAndSaveSummary의 흐름:
 * [1단: 짧은 읽기 tx] loadTargets → 대상 장소 목록 조회
 * [2단: tx 없음] 외부 OpenAI 호출 (AiSummaryServiceImpl.generateSummary) — DB 커넥션 미점유
 * [3단: 짧은 쓰기 tx] saveSummary → 결과 저장
 */
@Component
@RequiredArgsConstructor
public class AiSummaryTxHelper {

    private final RequestPlaceAggregateRepository rpaRepository;
    private final PlaceRepository placeRepository;
    private final PlaceSummaryRepository placeSummaryRepository;

    public record Target(Long placeId, String placeName, String categoryName) {}

    @Transactional(readOnly = true)
    public List<Target> loadTargets(String requestSlug) {
        var aggregates = rpaRepository.findAllByRequestId(requestSlug);
        return aggregates.stream()
                .filter(agg -> !placeSummaryRepository.existsById(agg.getPlaceId()))
                .map(agg -> {
                    Optional<Place> place = placeRepository.findById(agg.getPlaceId());
                    return place.map(p -> new Target(p.getId(), p.getPlaceName(), p.getCategoryName()))
                            .orElse(null);
                })
                .filter(t -> t != null)
                .toList();
    }

    @Transactional
    public void saveSummary(Long placeId, String summaryText) {
        placeRepository.findById(placeId).ifPresent(place -> {
            if (!placeSummaryRepository.existsById(placeId)) {
                PlaceSummary summary = PlaceSummary.builder()
                        .place(place)
                        .summaryText(summaryText)
                        .evidence("AI Generated based on basic info")
                        .build();
                placeSummaryRepository.save(summary);
            }
        });
    }
}
