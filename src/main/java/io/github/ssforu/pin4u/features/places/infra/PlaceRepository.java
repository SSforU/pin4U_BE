package io.github.ssforu.pin4u.features.places.infra;

import io.github.ssforu.pin4u.features.places.domain.Place;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    Optional<Place> findByExternalId(String externalId);
    List<Place> findByExternalIdIn(Collection<String> externalIds);
}
