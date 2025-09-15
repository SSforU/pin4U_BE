package io.github.ssforu.pin4u.features.member.application;

import io.github.ssforu.pin4u.features.member.domain.User;
import io.github.ssforu.pin4u.features.member.dto.MemberDtos;
import io.github.ssforu.pin4u.features.member.infra.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class MemberServiceImpl implements MemberService {
    private final UserRepository repo;
    public MemberServiceImpl(UserRepository repo) { this.repo = repo; }

    @Override
    @Transactional(readOnly = true)
    public MemberDtos.UserResponse getFixedUser() {
        User u = repo.findById(1L).orElseThrow(() ->
                new IllegalStateException("fixed user(id=1) not seeded"));
        return new MemberDtos.UserResponse(
                u.getId(), u.getNickname(), u.getPreferenceText(),
                u.getCreatedAt(), u.getUpdatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return repo.findById(id);
    }

    @Override
    public User updateNickname(Long id, String nickname) {
        User u = repo.findById(id).orElseThrow(() -> new NoSuchElementException("user_not_found"));
        u.setNickname(nickname);                // 엔티티에 setter 이미 있음 ✅
        return repo.save(u);                    // @UpdateTimestamp로 updated_at 자동 갱신
    }
}
