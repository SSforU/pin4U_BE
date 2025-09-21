package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.requests.domain.Request;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<Request, Long> {
    Optional<Request> findBySlug(String slug);

    // 💡 목록 화면 정렬 — created_at 역정렬
    List<Request> findAllByOrderByCreatedAtDesc();

    // 홈 대시보드: 내 요청 목록
    List<Request> findAllByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    // 그룹지도: 특정 그룹의 요청들
    List<Request> findAllByGroupId(Long groupId);
}
