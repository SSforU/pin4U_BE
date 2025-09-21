package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.requests.domain.RequestPlaceAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RequestPlaceAggregateRepository extends JpaRepository<RequestPlaceAggregate, Long> {

    Optional<RequestPlaceAggregate> findByRequestIdAndPlaceId(String requestId, Long placeId);

    interface SumRow {
        String getRequestId();
        Long getTotal();
    }

    @Query("""
           select a.requestId as requestId, sum(a.recommendedCount) as total
             from RequestPlaceAggregate a
            where a.requestId in :requestIds
            group by a.requestId
           """)
    List<SumRow> sumByRequestIds(@Param("requestIds") List<String> requestIds);

    @Query("""
           select coalesce(sum(a.recommendedCount), 0)
             from RequestPlaceAggregate a
            where a.requestId = :requestId
           """)
    Long sumByRequestId(@Param("requestId") String requestId);
}
