package io.github.ssforu.pin4u.features.requests.event;

public record RequestCreatedEvent(
        String requestSlug, // AI 분석 대상 (요청 ID)
        Long userId         // 요청자 (필요 시 알림용)
) {
}