package io.github.ssforu.pin4u.features.groups.infra;

import io.github.ssforu.pin4u.features.groups.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findBySlug(String slug);
    boolean existsBySlug(String slug);
    //추가함 - homeservice에 맞춤
    List<Group> findAllByOwnerUserId(Long ownerUserId);

}
