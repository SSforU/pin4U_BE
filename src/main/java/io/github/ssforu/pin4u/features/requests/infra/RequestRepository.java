// src/main/java/io/github/ssforu/pin4u/features/requests/infra/RequestRepository.java
package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.requests.domain.Request;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RequestRepository extends JpaRepository<Request, String> {
    List<Request> findAllByOrderByCreatedAtDesc();
}
