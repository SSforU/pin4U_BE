package io.github.ssforu.pin4u.features.places.infra;

import io.github.ssforu.pin4u.features.places.domain.PlaceMock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaceMockRepository extends JpaRepository<PlaceMock, Long> {
    // placeId = PK(=FK) 1:1 이면 기본 메서드로 충분
}
