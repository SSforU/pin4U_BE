package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.requests.domain.AiSummaryJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiSummaryJobRepository extends JpaRepository<AiSummaryJob, Long> {
    Optional<AiSummaryJob> findByRequestSlug(String requestSlug);
}
