package io.github.ssforu.pin4u.features.groups.application;

import io.github.ssforu.pin4u.features.groups.domain.Group;

public interface GroupService {
    Group createGroup(Long ownerUserId, String name, String imageUrl);
    void requestJoin(String groupSlug, Long userId);
    void approveMember(String groupSlug, Long approverUserId, Long targetUserId);
}
