// src/main/java/io/github/ssforu/pin4u/features/auth/dto/AuthDtos.java
package io.github.ssforu.pin4u.features.auth.dto;

public final class AuthDtos {
    public record KakaoLoginRequest(String accessToken) {}
    public record LoginUser(Long id, String nickname) {}
    public record LoginResponse(LoginUser user, boolean isNew) {}
}
