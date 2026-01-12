package io.github.ssforu.pin4u.features.notifications.application;

import io.github.ssforu.pin4u.features.notifications.dto.NotificationDtos.NotificationItem;
import io.github.ssforu.pin4u.features.notifications.dto.NotificationDtos.NotificationListResponse;
import io.github.ssforu.pin4u.features.notifications.infra.NotificationsQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationsQueryRepository query;

    @Override
    public NotificationListResponse listNotifications(Long ownerUserId, String status, Integer limit) {
        int lim = (limit == null || limit <= 0 || limit > 200) ? 20 : limit;
        String st = (status == null || status.isBlank()) ? "pending" : status.trim().toLowerCase();
        var rows = query.findNotificationsForOwner(ownerUserId, st, lim);

        var items = rows.stream().map(r -> new NotificationItem(
                r.getId(),
                r.getRequester_name(),
                r.getRequester_id(),
                r.getGroup_name(),
                r.getGroup_slug(),
                r.getCreated_at(),
                r.getStatus()
        )).toList();

        return new NotificationListResponse(items, items.size());
    }
}
