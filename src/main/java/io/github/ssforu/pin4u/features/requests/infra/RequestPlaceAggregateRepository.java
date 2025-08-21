// src/main/java/io/github/ssforu/pin4u/features/requests/infra/RequestPlaceAggregateRepository.java
package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.requests.domain.RequestPlaceAggregate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RequestPlaceAggregateRepository extends JpaRepository<RequestPlaceAggregate, Long> {
    Optional<RequestPlaceAggregate> findByRequestIdAndPlaceExternalId(String requestId, String placeExternalId);
}
