package io.github.ssforu.pin4u.common.response;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
        String result,
        T data,
        Error error,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", data, null, OffsetDateTime.now());
    }
    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        return new ApiResponse<>("error", null, new Error(code, message, details), OffsetDateTime.now());
    }
    public record Error(String code, String message, Object details) {}
}