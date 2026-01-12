package io.github.ssforu.pin4u.features.groups.application;

import io.github.ssforu.pin4u.features.groups.domain.Group;
import io.github.ssforu.pin4u.features.groups.dto.GroupDtos.MemberRequestListResponse;
import io.github.ssforu.pin4u.features.groups.dto.GroupDtos.MyMemberStatusResponse;

public interface GroupService {
    Group createGroup(Long ownerUserId, String name, String imageUrl);
    void requestJoin(String groupSlug, Long userId);
    void approveMember(String groupSlug, Long approverUserId, Long targetUserId);

    // 신규
    void rejectMember(String groupSlug, Long approverUserId, Long targetUserId);
    MyMemberStatusResponse getMyStatus(String groupSlug, Long me);
    MemberRequestListResponse listMemberRequests(String groupSlug, Long ownerUserId, String status, Integer limit);
}
