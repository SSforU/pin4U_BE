package io.github.ssforu.pin4u.features.notifications.application;

import io.github.ssforu.pin4u.features.notifications.dto.NotificationDtos.NotificationListResponse;

public interface NotificationService {
    NotificationListResponse listNotifications(Long ownerUserId, String status, Integer limit);
}
