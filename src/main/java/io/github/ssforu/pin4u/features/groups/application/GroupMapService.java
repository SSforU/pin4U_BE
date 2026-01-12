package io.github.ssforu.pin4u.features.groups.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ssforu.pin4u.features.groups.domain.Group;
import io.github.ssforu.pin4u.features.groups.domain.GroupMember;
import io.github.ssforu.pin4u.features.groups.domain.GroupMemberId;
import io.github.ssforu.pin4u.features.groups.infra.GroupMapQueryRepository;
import io.github.ssforu.pin4u.features.groups.infra.GroupMemberRepository;
import io.github.ssforu.pin4u.features.groups.infra.GroupRepository;
import io.github.ssforu.pin4u.features.places.application.MockAllocator;
import io.github.ssforu.pin4u.features.places.domain.PlaceMock;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupMapService {

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final RequestRepository requests;
    private final StationRepository stations;
    private final GroupMapQueryRepository query;
    private final ObjectMapper om;
    private final MockAllocator mockAllocator;

    @Transactional // mock 생성 필요시 write 허용
    public RequestDetailDtos.RequestDetailResponse getGroupMapAsRequestDetail(String groupSlug, Long me, Integer limit) {
        Group g = groups.findBySlug(groupSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "group not found"));

        // 권한: 오너 or 승인 멤버만
        if (!Objects.equals(g.getOwnerUserId(), me)) {
            var gmId = new GroupMemberId(g.getId(), me);
            var opt = members.findById(gmId);
            if (opt.isEmpty() || opt.get().getStatus() != GroupMember.Status.APPROVED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
            }
        }

        var groupReqs = requests.findAllByGroupId(g.getId());
        var groupBrief = new RequestDetailDtos.GroupBrief(g.getId(), g.getSlug(), g.getName(), g.getImageUrl());

        // 그룹 내 요청이 없으면 빈 리스트 반환 (slug는 group 표기)
        if (groupReqs == null || groupReqs.isEmpty()) {
            return new RequestDetailDtos.RequestDetailResponse(
                    "group:" + g.getSlug(),
                    new RequestDetailDtos.Station(null, null, null, null, null),
                    null,
                    List.of(),
                    groupBrief
            );
        }

        // 대표 요청(첫 요청 기준) – 이 요청의 slug를 응답에 실어 프론트가 노트 조회에 사용
        var first = groupReqs.get(0);
        var st = stations.findByCode(first.getStationCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "station not found for group"));

        int lim = (limit == null || limit <= 0 || limit > 100) ? 50 : limit;

        var rows = query.findItemsByGroupId(g.getId(), st.getLat().doubleValue(), st.getLng().doubleValue(), lim);

        var items = rows.stream().map(r -> {
            RequestDetailDtos.Mock mock = null;
            if (r.getMock_rating() != null || r.getMock_rating_count() != null
                    || r.getMock_image_urls_json() != null || r.getMock_opening_hours_json() != null) {
                mock = new RequestDetailDtos.Mock(
                        r.getMock_rating(),
                        r.getMock_rating_count(),
                        parseJsonArray(r.getMock_image_urls_json()),
                        parseJsonArray(r.getMock_opening_hours_json())
                );
            }
            return new RequestDetailDtos.Item(
                    r.getExternal_id(),
                    r.getId_stripped(),
                    r.getPlace_name(),
                    r.getCategory_group_code(),
                    r.getCategory_group_name(),
                    r.getCategory_name(),
                    r.getAddress_name(),
                    r.getRoad_address_name(),
                    r.getX(),
                    r.getY(),
                    r.getDistance_m(),
                    r.getPlace_url(),
                    mock,
                    null, // AI는 여기서 미포함
                    r.getRecommended_count()
            );
        }).collect(Collectors.toList());

        // mock 보장: 없는 항목만 생성 후 주입
        Set<String> need = items.stream()
                .filter(it -> it.mock() == null)
                .map(RequestDetailDtos.Item::externalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!need.isEmpty()) {
            Map<String, PlaceMock> ensured = mockAllocator.ensureMocks(need);
            items = items.stream().map(cur -> {
                if (cur.mock() != null) return cur;
                PlaceMock pm = ensured.get(cur.externalId());
                if (pm == null) return cur;
                var filled = new RequestDetailDtos.Mock(
                        pm.getRating() == null ? null : pm.getRating().doubleValue(),
                        pm.getRatingCount(),
                        parseJsonArray(pm.getImageUrlsJson()),
                        parseJsonArray(pm.getOpeningHoursJson())
                );
                return new RequestDetailDtos.Item(
                        cur.externalId(), cur.id(), cur.placeName(),
                        cur.categoryGroupCode(), cur.categoryGroupName(), cur.categoryName(),
                        cur.addressName(), cur.roadAddressName(),
                        cur.x(), cur.y(), cur.distanceM(), cur.placeUrl(),
                        filled, cur.ai(), cur.recommendedCount()
                );
            }).collect(Collectors.toList());
        }

        // ✅ 핵심: 응답 slug = 대표 request의 slug (노트/집계 라우팅과 Flyway V15 구조에 정확히 부합)
        return new RequestDetailDtos.RequestDetailResponse(
                first.getSlug(),
                new RequestDetailDtos.Station(st.getCode(), st.getName(), st.getLine(), st.getLat(), st.getLng()),
                first.getRequestMessage(), // 시연: 첫 요청의 메모를 메모 박스에 노출
                items,
                groupBrief
        );
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return om.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
