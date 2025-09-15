// src/main/java/io/github/ssforu/pin4u/features/member/infra/UserRepository.java
package io.github.ssforu.pin4u.features.member.infra;

import io.github.ssforu.pin4u.features.member.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByKakaoUserId(Long kakaoUserId);  // ★ 카카오 기준 upsert
}



/*
package io.github.ssforu.pin4u.features.member.infra;

import io.github.ssforu.pin4u.features.member.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {}
*/
