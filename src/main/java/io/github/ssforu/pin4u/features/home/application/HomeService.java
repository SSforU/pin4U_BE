// src/main/java/io/github/ssforu/pin4u/features/home/application/HomeService.java
package io.github.ssforu.pin4u.features.home.application;

import io.github.ssforu.pin4u.features.home.dto.HomeDtos;
import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceAggregateRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.stations.domain.Station;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import io.github.ssforu.pin4u.features.groups.infra.GroupMemberRepository;
import io.github.ssforu.pin4u.features.groups.infra.GroupRepository;
import io.github.ssforu.pin4u.features.groups.domain.Group;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final RequestRepository requests;
    private final StationRepository stations;
    private final RequestPlaceAggregateRepository rpaRepo;
    private final GroupMemberRepository gmRepo;
    private final GroupRepository groupRepo;
    private final JdbcTemplate jdbc; // 폴백용

    @Transactional(readOnly = true)
    public HomeDtos.DashboardResponse dashboard(Long me) {

        // 1) 개인지도(items): JPA → 네이티브 → 최소 폴백
        List<HomeDtos.Item> items = new ArrayList<>();

        // 1-1) JPA 1차
        try {
            var rows = Optional.ofNullable(
                    requests.findAllByOwnerUserIdAndGroupIdIsNullOrderByCreatedAtDesc(me)
            ).orElseGet(List::of);

            Map<String, Station> stByCode = new HashMap<>();
            for (Request r : rows) {
                Station st = null;
                try {
                    var code = r.getStationCode();
                    if (code != null && !code.isBlank()) {
                        st = stByCode.computeIfAbsent(code, c -> stations.findByCode(c).orElse(null));
                    }
                } catch (Exception ignored) { }

                int total = 0;
                try { total = Optional.ofNullable(rpaRepo.sumByRequestId(r.getSlug())).orElse(0L).intValue(); }
                catch (Exception ignored) { }

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
        } catch (Exception ignored) {
            items = List.of(); // 폴백으로
        }

        // 1-2) 네이티브 폴백
        if (items.isEmpty()) {
            try {
                items = jdbc.query("""
                    SELECT r.slug,
                           r.station_code,
                           r.created_at,
                           s.name AS station_name,
                           s.line AS station_line
                      FROM public.requests r
                 LEFT JOIN public.stations s ON s.code = r.station_code
                     WHERE r.owner_user_id = ? AND r.group_id IS NULL
                  ORDER BY r.created_at DESC
                     LIMIT 100
                """, (rs, i) -> {
                    String slug   = str(rs.getObject("slug"));
                    String stName = str(rs.getObject("station_name"));
                    String stLine = str(rs.getObject("station_line"));
                    OffsetDateTime createdAt = odt(rs.getTimestamp("created_at"));
                    int total = 0;
                    try { total = Optional.ofNullable(rpaRepo.sumByRequestId(slug)).orElse(0L).intValue(); }
                    catch (Exception ignored2) {}
                    return new HomeDtos.Item(slug, stName, stLine, null, total, createdAt, null);
                }, me);
            } catch (Exception ignored) {
                items = List.of();
            }
        }

        // 1-3) 최소 폴백
        if (items.isEmpty()) {
            try {
                items = jdbc.query("""
                    SELECT r.slug, r.created_at
                      FROM public.requests r
                     WHERE r.owner_user_id = ? AND r.group_id IS NULL
                  ORDER BY r.created_at DESC
                     LIMIT 50
                """, (rs, i) -> new HomeDtos.Item(
                        str(rs.getObject("slug")),
                        null, null, null, 0,
                        odt(rs.getTimestamp("created_at")),
                        null
                ), me);
            } catch (Exception ignored) {
                items = List.of();
            }
        }

        // 2) 그룹(groups): 오너 JPA → 멤버십 합침 → 네이티브 폴백
        Map<Long, Group> groupById = new LinkedHashMap<>();

        try {
            var ownerGroups = Optional.ofNullable(groupRepo.findAllByOwnerUserId(me)).orElseGet(List::of);
            for (Group g : ownerGroups) groupById.put(g.getId(), g);
        } catch (Exception ignored) { }

        List<Long> approvedIds = List.of();
        try { approvedIds = Optional.ofNullable(gmRepo.findApprovedGroupIdsByUserId(me)).orElseGet(List::of); }
        catch (Exception ignored) { }
        if (!approvedIds.isEmpty()) {
            try {
                for (Group g : groupRepo.findAllById(approvedIds)) {
                    if (g != null) groupById.putIfAbsent(g.getId(), g);
                }
            } catch (Exception ignored) { }
        }

        List<Map<String, Object>> groups;
        if (groupById.isEmpty()) {
            // 네이티브 폴백(오너 그룹)
            try {
                groups = jdbc.query("""
                    SELECT id, slug, name, image_url
                      FROM public.groups
                     WHERE owner_user_id = ?
                  ORDER BY id DESC
                     LIMIT 500
                """, (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", longOrNull(rs.getObject("id")));
                    m.put("slug", str(rs.getObject("slug")));
                    m.put("name", str(rs.getObject("name")));
                    m.put("image_url", str(rs.getObject("image_url")));
                    return m;
                }, me);
            } catch (Exception ignored) {
                groups = List.of();
            }
        } else {
            // ✅ 여기서 Map.of(...) 대신 명시적 Map으로 만들어서 제네릭 충돌 제거
            groups = new ArrayList<>(groupById.size());
            for (Group g : groupById.values()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", g.getId());
                m.put("slug", g.getSlug());
                m.put("name", g.getName());
                m.put("image_url", g.getImageUrl());
                groups.add(m);
            }
        }

        // 3) 배지 (시연용)
        Map<String, Integer> badges = Map.of("group_owner_pending", 0);

        return new HomeDtos.DashboardResponse(
                items != null ? items : List.of(),
                groups != null ? groups : List.of(),
                badges
        );
    }

    // ---------- 안전 변환 유틸 (예외 안 냄) ----------
    private static String str(Object o) { return (o == null) ? null : String.valueOf(o); }
    private static Long longOrNull(Object o) {
        try { return (o == null) ? null : Long.valueOf(String.valueOf(o)); }
        catch (Exception e) { return null; }
    }
    private static OffsetDateTime odt(Timestamp ts) {
        return (ts == null) ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
