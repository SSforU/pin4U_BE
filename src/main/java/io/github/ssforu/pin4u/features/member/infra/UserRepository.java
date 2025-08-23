package io.github.ssforu.pin4u.features.member.infra;

import io.github.ssforu.pin4u.features.member.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {}
