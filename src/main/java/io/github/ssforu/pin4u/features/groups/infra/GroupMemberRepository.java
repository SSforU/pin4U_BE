package io.github.ssforu.pin4u.features.groups.infra;

import io.github.ssforu.pin4u.features.groups.domain.GroupMember;
import io.github.ssforu.pin4u.features.groups.domain.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
    boolean existsById(GroupMemberId id);

    interface MemberRow {
        Long getUser_id();
        String getNickname();
        String getStatus();
        Instant getRequested_at();
        Instant getApproved_at();
    }

    @Query(value = """
            SELECT gm.user_id            AS user_id,
                   u.nickname            AS nickname,
                   gm.status::text       AS status,
                   gm.requested_at       AS requested_at,
                   gm.approved_at        AS approved_at
            FROM group_members gm
            JOIN users u ON u.id = gm.user_id
            WHERE gm.group_id = :groupId AND gm.status = 'pending'
            ORDER BY gm.requested_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<MemberRow> findPendingRows(@Param("groupId") Long groupId, @Param("limit") int limit);

    @Query(value = """
            SELECT gm.user_id            AS user_id,
                   u.nickname            AS nickname,
                   gm.status::text       AS status,
                   gm.requested_at       AS requested_at,
                   gm.approved_at        AS approved_at
            FROM group_members gm
            JOIN users u ON u.id = gm.user_id
            WHERE gm.group_id = :groupId AND gm.status = 'approved'
            ORDER BY gm.approved_at DESC NULLS LAST
            LIMIT :limit
            """, nativeQuery = true)
    List<MemberRow> findApprovedRows(@Param("groupId") Long groupId, @Param("limit") int limit);

    @Query(value = """
            SELECT gm.user_id            AS user_id,
                   u.nickname            AS nickname,
                   gm.status::text       AS status,
                   gm.requested_at       AS requested_at,
                   gm.approved_at        AS approved_at
            FROM group_members gm
            JOIN users u ON u.id = gm.user_id
            WHERE gm.group_id = :groupId
            ORDER BY gm.requested_at DESC NULLS LAST, gm.approved_at DESC NULLS LAST
            LIMIT :limit
            """, nativeQuery = true)
    List<MemberRow> findAllRows(@Param("groupId") Long groupId, @Param("limit") int limit);

    // ✅ 홈 대시보드용: 내가 '승인' 상태인 그룹들의 ID만 깔끔히 가져오기 (중복 방지)
    @Query(value = "SELECT DISTINCT group_id FROM group_members WHERE user_id = :userId AND status = 'approved'", nativeQuery = true)
    List<Long> findApprovedGroupIdsByUserId(@Param("userId") Long userId);
}
