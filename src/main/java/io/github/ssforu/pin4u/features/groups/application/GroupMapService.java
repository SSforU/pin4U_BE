package io.github.ssforu.pin4u.features.groups.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ssforu.pin4u.features.groups.domain.Group;
import io.github.ssforu.pin4u.features.groups.domain.GroupMember;
import io.github.ssforu.pin4u.features.groups.domain.GroupMemberId;
import io.github.ssforu.pin4u.features.groups.dto.GroupMapDtos;
import io.github.ssforu.pin4u.features.groups.infra.GroupMapQueryRepository;
import io.github.ssforu.pin4u.features.groups.infra.GroupRepository;
import io.github.ssforu.pin4u.features.groups.infra.GroupMemberRepository;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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

    public GroupMapDtos.Response getGroupMap(String groupSlug, Long me, Integer limit) {
        Group g = groups.findBySlug(groupSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "group not found"));

        // 권한: 오너 or 승인 멤버만
        if (!Objects.equals(g.getOwnerUserId(), me)) {
            var gmId = new GroupMemberId(g.getId(), me);
            var opt = members.findById(gmId);
            if (opt.isEmpty() || opt.get().getStatus() != GroupMember.Status.approved) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
            }
        }

        // ==== 중심 좌표: "그룹은 단일 역" 전제 ====
        var groupReqs = requests.findAllByGroupId(g.getId());  // 이미 존재하는 메서드(네가 추가했다고 했던 그 메서드)
        if (groupReqs == null || groupReqs.isEmpty()) {
            // 요청이 아직 없다면 빈 리스트 + center 없음
            return new GroupMapDtos.Response(
                    new GroupMapDtos.GroupBrief(g.getId(), g.getSlug(), g.getName(), g.getImageUrl()),
                    null,
                    java.util.List.of()
            );
        }

        // 그룹 내 모든 요청이 동일 역이라는 전제: 첫 요청의 역을 기준으로 사용
        String stationCode = groupReqs.get(0).getStationCode();
        var st = stations.findByCode(stationCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "station not found for group"));
        BigDecimal centerLat = st.getLat();
        BigDecimal centerLng = st.getLng();

        int lim = (limit == null || limit <= 0 || limit > 100) ? 50 : limit;

        var rows = query.findItemsByGroupId(g.getId(), centerLat.doubleValue(), centerLng.doubleValue(), lim);

        List<RequestDetailDtos.Item> items = rows.stream().map(r -> {
            RequestDetailDtos.Mock mock = new RequestDetailDtos.Mock(
                    r.getMock_rating(),
                    r.getMock_rating_count(),
                    parseJsonArray(r.getMock_image_urls_json()),
                    parseJsonArray(r.getMock_opening_hours_json())
            );
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
                    null, // AI 없음(요구사항대로)
                    r.getRecommended_count()
            );
        }).collect(Collectors.toList());

        return new GroupMapDtos.Response(
                new GroupMapDtos.GroupBrief(g.getId(), g.getSlug(), g.getName(), g.getImageUrl()),
                new GroupMapDtos.StationCenter(centerLat, centerLng),
                items
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
