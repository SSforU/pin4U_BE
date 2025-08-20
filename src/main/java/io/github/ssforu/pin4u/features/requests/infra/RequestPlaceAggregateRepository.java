package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.requests.domain.RequestPlaceAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RequestPlaceAggregateRepository extends JpaRepository<RequestPlaceAggregate, Long> {
    @Query("""
        select coalesce(sum(r.recommendCount), 0)
        from RequestPlaceAggregate r
        where r.requestId = :requestId
    """)
    long sumRecommendCountByRequestId(Long requestId);
}
