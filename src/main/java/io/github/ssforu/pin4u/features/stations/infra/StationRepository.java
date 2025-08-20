package io.github.ssforu.pin4u.features.stations.infra;

import io.github.ssforu.pin4u.features.stations.domain.Station;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, String> {
    Optional<Station> findByCode(String code);
    List<Station> findAllByCodeIn(Collection<String> codes);
    Page<Station> searchByNameContains(String q, Pageable pageable);
}

