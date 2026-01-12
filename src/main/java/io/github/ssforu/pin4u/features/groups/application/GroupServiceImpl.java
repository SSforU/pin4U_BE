// src/main/java/io/github/ssforu/pin4u/features/groups/application/GroupServiceImpl.java
package io.github.ssforu.pin4u.features.groups.application;

import io.github.ssforu.pin4u.features.groups.domain.Group;
import io.github.ssforu.pin4u.features.groups.domain.GroupMember;
import io.github.ssforu.pin4u.features.groups.domain.GroupMemberId;
import io.github.ssforu.pin4u.features.groups.dto.GroupDtos.MemberRequestItem;
import io.github.ssforu.pin4u.features.groups.dto.GroupDtos.MemberRequestListResponse;
import io.github.ssforu.pin4u.features.groups.dto.GroupDtos.MyMemberStatusResponse;
import io.github.ssforu.pin4u.features.groups.infra.GroupMemberRepository;
import io.github.ssforu.pin4u.features.groups.infra.GroupRepository;
import io.github.ssforu.pin4u.features.requests.infra.SlugGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;

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

    // ★ DDL과 정합: groups.slug VARCHAR(24)
    private static final int SLUG_MAX = 24;
    private static final String SAFE = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";
    private static final SecureRandom RND = new SecureRandom();

    private String sanitize(String raw) {
        if (raw == null) return "g";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (SAFE.indexOf(c) >= 0) sb.append(c);
        }
        String s = (sb.length() == 0) ? "g" : sb.toString();
        return (s.length() > SLUG_MAX) ? s.substring(0, SLUG_MAX) : s;
    }

    private String withRandomTail(String base, int tailLen) {
        if (tailLen <= 0) return base;
        StringBuilder t = new StringBuilder(tailLen);
        for (int i = 0; i < tailLen; i++) t.append(SAFE.charAt(RND.nextInt(SAFE.length())));
        int keep = Math.max(1, SLUG_MAX - tailLen - 1);
        String head = base.length() > keep ? base.substring(0, keep) : base;
        return head + "-" + t;
    }

    @Override
    public Group createGroup(Long ownerUserId, String name, String imageUrl) {
        if (ownerUserId == null) {
            // 컨트롤러에서 이미 게이트하지만 방어적으로 401
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name_required");
        }

        // 1) 기존 제너레이터 유지 + DDL 적합 보정
        String generated = slugGenerator.generate(name.trim());
        String slug = sanitize(generated);

        // 2) 사전 충돌 회피 (경합 방지)
        for (int i = 0; i < 6 && groups.existsBySlug(slug); i++) {
            slug = withRandomTail(slug, 3 + i);
        }

        Group saved;
        try {
            saved = groups.save(new Group(slug, name.trim(), imageUrl, ownerUserId));
        } catch (DataIntegrityViolationException ex) {
            // 마지막 방어 1회
            String fallback = withRandomTail(slug, 8);
            saved = groups.save(new Group(fallback, name.trim(), imageUrl, ownerUserId));
        }

        // 3) 오너 멤버십(멱등)
        GroupMemberId ownerId = new GroupMemberId(saved.getId(), ownerUserId);
        if (!members.existsById(ownerId)) {
            members.save(new GroupMember(
                    saved.getId(), ownerUserId,
                    GroupMember.Role.OWNER,
                    GroupMember.Status.APPROVED
            ));
        }
        return saved;
    }

    @Override
    public void requestJoin(String groupSlug, Long userId) {
        Group g = groups.findBySlug(groupSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "group_not_found"));

        GroupMemberId id = new GroupMemberId(g.getId(), userId);
        if (members.existsById(id)) return; // 멱등

        try {
            members.save(new GroupMember(
                    g.getId(), userId,
                    GroupMember.Role.MEMBER,
                    GroupMember.Status.PENDING
            ));
        } catch (DataIntegrityViolationException ignore) {
            // 유니크 경합 등은 멱등 취급
        }
    }

    @Override
    public void approveMember(String groupSlug, Long approverUserId, Long targetUserId) {
        Group g = groups.findBySlug(groupSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "group_not_found"));

        if (!g.getOwnerUserId().equals(approverUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }

        GroupMemberId id = new GroupMemberId(g.getId(), targetUserId);
        GroupMember m = members.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "membership_not_found"));

        m.approve(); // dirty checking → Status.APPROVED
    }

    @Override
    public void rejectMember(String groupSlug, Long approverUserId, Long targetUserId) {
        Group g = groups.findBySlug(groupSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "group_not_found"));

        if (!g.getOwnerUserId().equals(approverUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }

        GroupMemberId id = new GroupMemberId(g.getId(), targetUserId);
        members.findById(id).ifPresent(members::delete); // 의도: 대기 거절/승인멤버 퇴출 모두 허용
    }

    @Override
    public MyMemberStatusResponse getMyStatus(String groupSlug, Long me) {
        Group g = groups.findBySlug(groupSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "group_not_found"));

        if (g.getOwnerUserId().equals(me)) {
            return new MyMemberStatusResponse("approved", "owner");
        }

        var id = new GroupMemberId(g.getId(), me);
        var opt = members.findById(id);
        if (opt.isEmpty()) return new MyMemberStatusResponse("none", null);

        var m = opt.get();
        return new MyMemberStatusResponse(
                m.getStatus().name().toLowerCase(),
                m.getRole().name().toLowerCase()
        );
    }

    @Override
    public MemberRequestListResponse listMemberRequests(String groupSlug, Long ownerUserId, String status, Integer limit) {
        Group g = groups.findBySlug(groupSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "group_not_found"));

        if (!g.getOwnerUserId().equals(ownerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }

        int lim = (limit == null || limit <= 0 || limit > 200) ? 20 : limit;
        String normalized = (status == null ? "pending" : status.trim().toLowerCase());

        List<GroupMemberRepository.MemberRow> rows = switch (normalized) {
            case "approved" -> members.findApprovedRows(g.getId(), lim);
            case "all"      -> members.findAllRows(g.getId(), lim);
            default         -> members.findPendingRows(g.getId(), lim);
        };

        var items = rows.stream()
                .map(r -> new MemberRequestItem(
                        r.getUser_id(), r.getNickname(), r.getStatus(),
                        r.getRequested_at(), r.getApproved_at()
                ))
                .sorted(Comparator.comparing(
                        MemberRequestItem::requested_at,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

        return new MemberRequestListResponse(items, items.size());
    }
}
