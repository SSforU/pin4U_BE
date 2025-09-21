package io.github.ssforu.pin4u.features.groups.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class GroupMemberId implements Serializable {
    private Long groupId;
    private Long userId;

    public GroupMemberId() {}
    public GroupMemberId(Long groupId, Long userId) {
        this.groupId = groupId; this.userId = userId;
    }

    public Long getGroupId() { return groupId; }
    public Long getUserId() { return userId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupMemberId that)) return false;
        return Objects.equals(groupId, that.groupId) && Objects.equals(userId, that.userId);
    }
    @Override public int hashCode() { return Objects.hash(groupId, userId); }
}
