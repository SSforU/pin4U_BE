package io.github.ssforu.pin4u.features.auth.application;

import io.github.ssforu.pin4u.features.auth.dto.AuthDtos;
import io.github.ssforu.pin4u.features.auth.infra.KakaoOAuthClient;
import io.github.ssforu.pin4u.features.member.domain.User;
import io.github.ssforu.pin4u.features.member.infra.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final KakaoOAuthClient kakao;
    private final UserRepository users;

    public AuthServiceImpl(KakaoOAuthClient kakao, UserRepository users) {
        this.kakao = kakao;
        this.users = users;
    }

    @Override
    @Transactional
    public AuthDtos.LoginResponse loginWithKakaoToken(String accessToken) {
        var me = kakao.getMe(accessToken).block();
        if (me == null) throw new IllegalArgumentException("kakao_me_null");

        Long kakaoId = me.id();
        String kakaoNickname =
                me.kakao_account() != null &&
                        me.kakao_account().profile() != null &&
                        me.kakao_account().profile().nickname() != null &&
                        !me.kakao_account().profile().nickname().isBlank()
                        ? me.kakao_account().profile().nickname().trim()
                        : "게스트";

        var found = users.findByKakaoUserId(kakaoId);
        if (found.isPresent()) {
            User u = found.get();

            // ✅ 기존 유저: 닉네임을 덮어쓰지 않는다.
            //    다만 DB 닉네임이 비어있다면(초기 마이그레이션 등) 한 번만 보수적으로 채워준다.
            if (u.getNickname() == null || u.getNickname().isBlank()) {
                u.setNickname(kakaoNickname);
            }

            return new AuthDtos.LoginResponse(
                    new AuthDtos.LoginUser(u.getId(), u.getNickname()),
                    false
            );
        }

        // ✅ 신규 가입: 최초 1회만 카카오 닉네임(없으면 '게스트')로 초기화
        User created = users.save(new User(kakaoId, kakaoNickname, ""));
        return new AuthDtos.LoginResponse(
                new AuthDtos.LoginUser(created.getId(), created.getNickname()),
                true
        );
    }
}
