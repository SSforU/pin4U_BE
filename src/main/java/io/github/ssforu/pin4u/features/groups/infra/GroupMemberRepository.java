package io.github.ssforu.pin4u.features.groups.infra;

import io.github.ssforu.pin4u.features.groups.domain.GroupMember;
import io.github.ssforu.pin4u.features.groups.domain.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
    boolean existsById(GroupMemberId id);
}
