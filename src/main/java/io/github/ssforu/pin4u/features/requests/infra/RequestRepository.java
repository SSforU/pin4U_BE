package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.requests.domain.Request;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<Request, Long> {
    Optional<Request> findBySlug(String slug);

    // 목록 화면 정렬 — created_at 역정렬
    List<Request> findAllByOrderByCreatedAtDesc();

    // 홈 대시보드: 내 요청(전체)
    List<Request> findAllByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    // ✅ 홈 대시보드: '개인지도'만 — group_id IS NULL
    List<Request> findAllByOwnerUserIdAndGroupIdIsNullOrderByCreatedAtDesc(Long ownerUserId);

    // 그룹지도: 특정 그룹의 요청들
    List<Request> findAllByGroupId(Long groupId);

    // ✅ 추가: 그룹 내에서 external_id 를 포함하는 request.slug 하나 찾기 (노트 조회용)
    @Query(value = """
        SELECT r.slug
        FROM requests r
        JOIN request_place_aggregates rpa ON rpa.request_id = r.slug
        JOIN places p ON p.id = rpa.place_id
        WHERE r.group_id = :groupId
          AND p.external_id = :externalId
        ORDER BY r.created_at ASC
        LIMIT 1
        """, nativeQuery = true)
    String findAnyRequestSlugByGroupIdAndExternalId(
            @Param("groupId") Long groupId,
            @Param("externalId") String externalId
    );
}
