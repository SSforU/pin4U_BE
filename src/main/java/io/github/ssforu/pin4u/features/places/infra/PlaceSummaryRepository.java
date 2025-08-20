package io.github.ssforu.pin4u.features.places.infra;

import io.github.ssforu.pin4u.features.places.domain.PlaceSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlaceSummaryRepository extends JpaRepository<PlaceSummary, Long> {
    Optional<PlaceSummary> findByPlaceId(Long placeId);
}
