// src/main/java/io/github/ssforu/pin4u/features/recommendations/infra/RecommendationNoteRepository.java
package io.github.ssforu.pin4u.features.recommendations.infra;

import io.github.ssforu.pin4u.features.recommendations.domain.RecommendationNote;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationNoteRepository extends JpaRepository<RecommendationNote, Long> {
    boolean existsByRpaIdAndGuestId(Long rpaId, UUID guestId);
}
