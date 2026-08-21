package io.github.ssforu.pin4u.features.home.application;

import io.github.ssforu.pin4u.features.groups.domain.Group;
import io.github.ssforu.pin4u.features.groups.infra.GroupMemberRepository;
import io.github.ssforu.pin4u.features.groups.infra.GroupRepository;
import io.github.ssforu.pin4u.features.home.dto.HomeDtos;
import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceAggregateRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.stations.domain.Station;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final RequestRepository requests;
    private final StationRepository stations;
    private final RequestPlaceAggregateRepository rpaRepo;
    private final GroupMemberRepository gmRepo;
    private final GroupRepository groupRepo;

    @Transactional(readOnly = true)
    public HomeDtos.DashboardResponse dashboard(Long me) {

        // 1) 개인지도(items)
        var rows = Optional.ofNullable(
                requests.findAllByOwnerUserIdAndGroupIdIsNullOrderByCreatedAtDesc(me)
        ).orElseGet(List::of);

        // 역 정보: 코드별 1회 조회 후 캐시
        Map<String, Station> stByCode = new HashMap<>();
        for (Request r : rows) {
            String code = r.getStationCode();
            if (code != null && !code.isBlank()) {
                stByCode.computeIfAbsent(code, c -> stations.findByCode(c).orElse(null));
            }
        }

        // 추천 수 집계: 배치 1회 호출로 O(N) → O(1)
        List<String> slugs = rows.stream().map(Request::getSlug).toList();
        Map<String, Long> sumMap = slugs.isEmpty() ? Map.of()
                : rpaRepo.sumByRequestIds(slugs).stream()
                        .collect(Collectors.toMap(
                                RequestPlaceAggregateRepository.SumRow::getRequestId,
                                RequestPlaceAggregateRepository.SumRow::getTotal));

        List<HomeDtos.Item> items = new ArrayList<>(rows.size());
        for (Request r : rows) {
            Station st = stByCode.get(r.getStationCode());
            int total = Optional.ofNullable(sumMap.get(r.getSlug())).orElse(0L).intValue();

            items.add(new HomeDtos.Item(
                    r.getSlug(),
                    st != null ? st.getName() : null,
                    st != null ? st.getLine() : null,
                    null,
                    total,
                    r.getCreatedAt(),
                    r.getRequestMessage()
            ));
        }

        // 2) 그룹(groups): 오너 + 멤버십 합침
        Map<Long, Group> groupById = new LinkedHashMap<>();

        var ownerGroups = Optional.ofNullable(groupRepo.findAllByOwnerUserId(me)).orElseGet(List::of);
        for (Group g : ownerGroups) groupById.put(g.getId(), g);

        List<Long> approvedIds = Optional.ofNullable(gmRepo.findApprovedGroupIdsByUserId(me)).orElseGet(List::of);
        if (!approvedIds.isEmpty()) {
            for (Group g : groupRepo.findAllById(approvedIds)) {
                if (g != null) groupById.putIfAbsent(g.getId(), g);
            }
        }

        List<Map<String, Object>> groups = new ArrayList<>(groupById.size());
        for (Group g : groupById.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("slug", g.getSlug());
            m.put("name", g.getName());
            m.put("image_url", g.getImageUrl());
            groups.add(m);
        }

        // 3) 배지
        Map<String, Integer> badges = Map.of("group_owner_pending", 0);

        return new HomeDtos.DashboardResponse(items, groups, badges);
    }
}
