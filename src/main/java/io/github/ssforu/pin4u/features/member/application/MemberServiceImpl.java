package io.github.ssforu.pin4u.features.member.application;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.member.domain.User;
import io.github.ssforu.pin4u.features.member.dto.MemberDtos;
import io.github.ssforu.pin4u.features.member.infra.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class MemberServiceImpl implements MemberService {
    private final UserRepository repo;
    public MemberServiceImpl(UserRepository repo) { this.repo = repo; }

    @Override
    public MemberDtos.UserResponse getFixedUser() {
        User u = repo.findById(1L).orElseThrow(() ->
                new IllegalStateException("fixed user(id=1) not seeded"));
        return new MemberDtos.UserResponse(
                u.getId(), u.getNickname(), u.getPreferenceText(),
                u.getCreatedAt(), u.getUpdatedAt()
        );
    }
}
