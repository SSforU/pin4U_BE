package io.github.ssforu.pin4u.features.member.application;

import io.github.ssforu.pin4u.features.member.dto.MemberDtos;
import io.github.ssforu.pin4u.features.member.domain.User;

import java.util.Optional;

public interface MemberService {
    // 기존 고정 유저 응답(데모/호환)
    MemberDtos.UserResponse getFixedUser();

    // me GET/PATCH를 위한 최소 메서드 2개
    Optional<User> findById(Long id);
    User updateNickname(Long id, String nickname);
}
