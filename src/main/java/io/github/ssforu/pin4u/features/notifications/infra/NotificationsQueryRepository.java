package io.github.ssforu.pin4u.features.notifications.infra;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationsQueryRepository extends Repository<io.github.ssforu.pin4u.features.groups.domain.Group, Long> {

    interface Row {
        String getId();
        String getRequester_name();
        Long getRequester_id();
        String getGroup_name();
        String getGroup_slug();
        Instant getCreated_at();
        String getStatus();
    }

    @Query(value = """
        SELECT
          'gm:' || gm.group_id || ':' || gm.user_id || ':' || EXTRACT(EPOCH FROM gm.requested_at)::bigint AS id,
          u.nickname   AS requester_name,
          gm.user_id   AS requester_id,
          g.name       AS group_name,
          g.slug       AS group_slug,
          gm.requested_at AS created_at,
          gm.status::text AS status
        FROM group_members gm
        JOIN groups g ON g.id = gm.group_id
        JOIN users  u ON u.id = gm.user_id
        WHERE g.owner_user_id = :ownerId
          AND (:status = 'all' OR gm.status::text = :status)
        ORDER BY gm.requested_at DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<Row> findNotificationsForOwner(
            @Param("ownerId") Long ownerId,
            @Param("status") String status,     // pending | approved | all
            @Param("limit") int limit
    );
}
