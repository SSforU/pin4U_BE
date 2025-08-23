// src/main/java/io/github/ssforu/pin4u/features/stations/infra/StationRepository.java
package io.github.ssforu.pin4u.features.stations.infra;

import io.github.ssforu.pin4u.features.stations.domain.Station;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, String> {
    Optional<Station> findByCode(String code);
    List<Station> findAllByCodeIn(Collection<String> codes);

    // ⚠️ Spring Data 규칙에 맞는 파생 쿼리 이름으로 교체
    Page<Station> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // ✅ fallback용
    Optional<Station> findByNameAndLine(String name, String line);
    List<Station> findAllByName(String name);
}
