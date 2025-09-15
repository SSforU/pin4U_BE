package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.requests.domain.RequestPlaceAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RequestPlaceAggregateRepository extends JpaRepository<RequestPlaceAggregate, Long> {

    Optional<RequestPlaceAggregate> findByRequestIdAndPlaceId(String requestId, Long placeId); // [FIX] externalId → placeId

    // 목록용: 여러 요청(slug)에 대한 합계
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




/* 2차
package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.requests.domain.RequestPlaceAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RequestPlaceAggregateRepository extends JpaRepository<RequestPlaceAggregate, Long> {

    // [FIX] 엔티티/DDL은 place_id(BIGINT) 이므로 메서드 명과 파라미터 타입을 placeId(Long)로 맞춤
    Optional<RequestPlaceAggregate> findByRequestIdAndPlaceId(String requestId, Long placeId);

    // 목록용: 여러 요청(slug)에 대한 합계
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

    // 단건용: 한 요청(slug)의 합계
    @Query("""
           select coalesce(sum(a.recommendedCount), 0)
             from RequestPlaceAggregate a
            where a.requestId = :requestId
           """)
    Long sumByRequestId(@Param("requestId") String requestId);
}
*/



/* 1차
package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.requests.domain.RequestPlaceAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RequestPlaceAggregateRepository extends JpaRepository<RequestPlaceAggregate, Long> {

    Optional<RequestPlaceAggregate> findByRequestIdAndPlaceExternalId(String requestId, String placeExternalId);

    // 목록용: 여러 요청(slug)에 대한 합계
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

    // 단건용: 한 요청(slug)의 합계
    @Query("""
           select coalesce(sum(a.recommendedCount), 0)
             from RequestPlaceAggregate a
            where a.requestId = :requestId
           """)
    Long sumByRequestId(@Param("requestId") String requestId);
}
*/
