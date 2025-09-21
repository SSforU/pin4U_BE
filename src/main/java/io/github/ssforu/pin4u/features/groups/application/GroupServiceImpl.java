package io.github.ssforu.pin4u.features.groups.application;

import io.github.ssforu.pin4u.features.groups.domain.Group;
import io.github.ssforu.pin4u.features.groups.domain.GroupMember;
import io.github.ssforu.pin4u.features.groups.domain.GroupMemberId;
import io.github.ssforu.pin4u.features.groups.infra.GroupMemberRepository;
import io.github.ssforu.pin4u.features.groups.infra.GroupRepository;
import io.github.ssforu.pin4u.features.requests.infra.SlugGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final SlugGenerator slugGenerator;

    public GroupServiceImpl(GroupRepository groups, GroupMemberRepository members, SlugGenerator slugGenerator) {
        this.groups = groups;
        this.members = members;
        this.slugGenerator = slugGenerator;
    }

    @Override
    public Group createGroup(Long ownerUserId, String name, String imageUrl) {
        if (ownerUserId == null) throw new IllegalArgumentException("unauthorized");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name_required");

        // slug는 이름 기반으로 생성(유일성은 DB UNIQUE가 보장)
        String slugSeed = name.trim();
        String slug = slugGenerator.generate(slugSeed);

        Group g = new Group(slug, name.trim(), imageUrl, ownerUserId);
        Group saved = groups.save(g);

        // 소유자 멤버십(approved/owner) 자동 생성
        GroupMember owner = new GroupMember(saved.getId(), ownerUserId,
                GroupMember.Role.owner, GroupMember.Status.approved);
        members.save(owner);

        return saved;
    }

    @Override
    public void requestJoin(String groupSlug, Long userId) {
        Group g = groups.findBySlug(groupSlug)
                .orElseThrow(() -> new IllegalArgumentException("group_not_found"));

        GroupMemberId id = new GroupMemberId(g.getId(), userId);

        if (members.existsById(id)) return; // 멱등

        try {
            members.save(new GroupMember(g.getId(), userId,
                    GroupMember.Role.member, GroupMember.Status.pending));
        } catch (DataIntegrityViolationException ignore) {
            // 경쟁조건 시 멱등 처리
        }
    }

    @Override
    public void approveMember(String groupSlug, Long approverUserId, Long targetUserId) {
        Group g = groups.findBySlug(groupSlug)
                .orElseThrow(() -> new IllegalArgumentException("group_not_found"));
        if (!g.getOwnerUserId().equals(approverUserId))
            throw new IllegalArgumentException("forbidden");

        GroupMemberId id = new GroupMemberId(g.getId(), targetUserId);
        GroupMember m = members.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("membership_not_found"));

        m.approve(); // JPA dirty checking으로 업데이트
    }
}
