package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.requests.domain.AiSummaryJob;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSummaryJobRepository extends JpaRepository<AiSummaryJob, Long> {
    Optional<AiSummaryJob> findByRequestSlug(String requestSlug);
}
