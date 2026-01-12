package io.github.ssforu.pin4u.features.places.application;

import io.github.ssforu.pin4u.features.places.domain.PlaceMock;
import io.github.ssforu.pin4u.features.places.infra.PlaceMockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MockAllocator {

    private final PlaceMockRepository mocks;
    private final MockDataGenerator gen;

    public MockAllocator(PlaceMockRepository mocks, MockDataGenerator gen) {
        this.mocks = mocks;
        this.gen = gen;
    }

    /**
     * 주어진 externalId들에 대해 place_mock이 없다면 생성하여 보장.
     * @return externalId -> PlaceMock 매핑(새로 만든 것도 포함)
     */
    @Transactional
    public Map<String, PlaceMock> ensureMocks(Collection<String> externalIds) {
        if (externalIds == null || externalIds.isEmpty()) return Map.of();

        List<String> ids = externalIds.stream().filter(Objects::nonNull).distinct().toList();

        Map<String, PlaceMock> existing = mocks.findByExternalIdIn(ids).stream()
                .collect(Collectors.toMap(PlaceMock::getExternalId, it -> it));

        List<PlaceMock> toSave = new ArrayList<>();
        for (String id : ids) {
            if (existing.containsKey(id)) continue;

            PlaceMock pm = PlaceMock.builder()
                    .externalId(id)
                    .rating(gen.randomRating(id))
                    .ratingCount(gen.randomRatingCount(id))
                    .reviewSnippetsJson(gen.randomReviewSnippetJson(id))
                    .imageUrlsJson(null) // S3 라인은 프론트/업로더에서
                    .openingHoursJson(gen.randomOpeningHoursJson(id))
                    .updatedAt(OffsetDateTime.now())
                    .build();

            toSave.add(pm);
            existing.put(id, pm);
        }

        if (!toSave.isEmpty()) {
            // FK: place_mock.external_id → places.external_id (존재 가정)
            mocks.saveAll(toSave);
        }
        return existing;
    }
}
