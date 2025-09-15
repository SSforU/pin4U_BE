// src/main/java/io/github/ssforu/pin4u/features/auth/application/AuthService.java
package io.github.ssforu.pin4u.features.auth.application;

import io.github.ssforu.pin4u.features.auth.dto.AuthDtos;

public interface AuthService {
    AuthDtos.LoginResponse loginWithKakaoToken(String accessToken);
}
