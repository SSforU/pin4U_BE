// src/main/java/io/github/ssforu/pin4u/features/groups/domain/GroupMember.java
package io.github.ssforu.pin4u.features.groups.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "group_members")
public class GroupMember {

    // 자바 enum은 관례대로 UPPERCASE로 선언
    public enum Role { OWNER, MEMBER }
    public enum Status { PENDING, APPROVED }

    @EmbeddedId
    private GroupMemberId id;

    // ★ @Enumerated 제거, 컨버터 명시
    @Convert(converter = RoleConverter.class)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    @Convert(converter = StatusConverter.class)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected GroupMember() {}

    public GroupMember(Long groupId, Long userId, Role role, Status status) {
        this.id = new GroupMemberId(groupId, userId);
        this.role = role;
        this.status = status;
        this.requestedAt = Instant.now();
    }

    public GroupMemberId getId() { return id; }
    public Role getRole() { return role; }
    public Status getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getApprovedAt() { return approvedAt; }

    public void approve() {
        this.status = Status.APPROVED;  // 자바는 대문자 enum
        this.approvedAt = Instant.now();
    }
}
