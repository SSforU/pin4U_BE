// src/main/java/io/github/ssforu/pin4u/features/recommendations/infra/RecommendationNoteRepository.java
package io.github.ssforu.pin4u.features.recommendations.infra;

import io.github.ssforu.pin4u.features.recommendations.domain.RecommendationNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecommendationNoteRepository extends JpaRepository<RecommendationNote, Long> {
    boolean existsByRpaIdAndGuestId(Long rpaId, UUID guestId);
}
