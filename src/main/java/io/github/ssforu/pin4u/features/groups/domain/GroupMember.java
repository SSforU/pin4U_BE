package io.github.ssforu.pin4u.features.groups.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "group_members")
public class GroupMember {

    public enum Role { owner, member }                 // DB CHECK와 값 일치 (소문자)
    public enum Status { pending, approved }           // DB CHECK와 값 일치 (소문자)

    @EmbeddedId
    private GroupMemberId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
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
        this.status = Status.approved;
        this.approvedAt = Instant.now();
    }
}
