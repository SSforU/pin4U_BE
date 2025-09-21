// src/main/java/io/github/ssforu/pin4u/features/auth/dto/AuthDtos.java
package io.github.ssforu.pin4u.features.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoLoginRequest(
            @NotBlank
            @JsonAlias({"access_token", "accessToken"})
            String accessToken
    ) {}

    public record LoginUser(Long id, String nickname) {}

    // isNew: 이번 로그인에서 신규 가입(upsert)인지 여부
    public record LoginResponse(LoginUser user, boolean isNew) {}
}
