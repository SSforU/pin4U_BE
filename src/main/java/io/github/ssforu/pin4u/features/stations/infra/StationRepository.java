package io.github.ssforu.pin4u.features.stations.infra;

import io.github.ssforu.pin4u.features.stations.domain.Station;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, String> {

    Optional<Station> findByCode(String code);

    // ✅ [Fix] 이 메서드가 없어서 에러가 났습니다. 추가해주세요.
    boolean existsByCode(String code);

    List<Station> findAllByCodeIn(Collection<String> codes);

    // ⚠️ Spring Data 규칙에 맞는 파생 쿼리
    Page<Station> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // ✅ fallback용
    Optional<Station> findByNameAndLine(String name, String line);
    List<Station> findAllByName(String name);
}