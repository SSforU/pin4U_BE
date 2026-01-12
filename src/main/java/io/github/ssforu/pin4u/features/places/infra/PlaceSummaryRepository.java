// src/main/java/io/github/ssforu/pin4u/features/places/infra/PlaceSummaryRepository.java
package io.github.ssforu.pin4u.features.places.infra;

import io.github.ssforu.pin4u.features.places.domain.PlaceSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlaceSummaryRepository extends JpaRepository<PlaceSummary, Long> {
    // PK(=place_id)로 조회 — 기본 findById(Long)와 동일 목적
    Optional<PlaceSummary> findByPlace_Id(Long placeId);

    // 편의: 외부ID만 있을 때 한 방에 조회
    Optional<PlaceSummary> findByPlace_ExternalId(String externalId);
}
