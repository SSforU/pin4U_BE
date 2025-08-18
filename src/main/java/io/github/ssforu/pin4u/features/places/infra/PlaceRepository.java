package io.github.ssforu.pin4u.features.places.infra;

import io.github.ssforu.pin4u.features.places.domain.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {
    Optional<Place> findByExternalId(String externalId);
    boolean existsByExternalId(String externalId);

    // 반경/바운딩박스 검색 대비(추후 사용 가능)
    List<Place> findTop10ByYBetweenAndXBetweenOrderByIdAsc(
            BigDecimal yMin, BigDecimal yMax,
            BigDecimal xMin, BigDecimal xMax
    );
}
