package io.github.ssforu.pin4u.features.places.infra;

import io.github.ssforu.pin4u.features.places.domain.PlaceMock;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceMockRepository extends JpaRepository<PlaceMock, String> {
    List<PlaceMock> findByExternalIdIn(Collection<String> externalIds);
}
