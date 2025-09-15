// src/main/java/io/github/ssforu/pin4u/features/requests/infra/RequestRepository.java
package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.requests.domain.Request;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
//string에서 long으로 수정
public interface RequestRepository extends JpaRepository<Request, Long> {
    Optional<Request> findBySlug(String slug);

    // 💡 목록 화면 정렬용 — created_at 역정렬
    List<Request> findAllByOrderByCreatedAtDesc();
}
