// src/main/java/io/github/ssforu/pin4u/features/auth/application/AuthServiceImpl.java
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
        var me = kakao.getMe(accessToken).block(); // 데모: 동기블록 (간단화)
        if (me == null) throw new IllegalArgumentException("kakao_me_null");
        Long kakaoId = me.id();
        String nickname =
                me.kakao_account() != null && me.kakao_account().profile() != null
                        ? me.kakao_account().profile().nickname()
                        : "게스트";

        var found = users.findByKakaoUserId(kakaoId);
        if (found.isPresent()) {
            User u = found.get();
            // 닉네임 변경이 있으면 반영(선택)
            if (nickname != null && !nickname.isBlank() && !nickname.equals(u.getNickname())) {
                u.setNickname(nickname);
            }
            return new AuthDtos.LoginResponse(new AuthDtos.LoginUser(u.getId(), u.getNickname()), false);
        }

        // 새 사용자 생성: preference_text는 빈 문자열로
        User created = users.save(new User(kakaoId, nickname, ""));
        return new AuthDtos.LoginResponse(new AuthDtos.LoginUser(created.getId(), created.getNickname()), true);
    }
}
