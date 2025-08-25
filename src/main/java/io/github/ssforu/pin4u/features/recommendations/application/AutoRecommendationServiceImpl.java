package io.github.ssforu.pin4u.features.recommendations.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ssforu.pin4u.features.places.application.PlaceSearchService;
import io.github.ssforu.pin4u.features.places.dto.PlaceDtos;
import io.github.ssforu.pin4u.features.requests.application.AiSummaryService;
import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos.Ai;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos.Item;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos.Mock;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos.RequestDetailResponse;
import io.github.ssforu.pin4u.features.requests.infra.RequestDetailQueryRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceNotesQueryRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.stations.domain.Station;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AutoRecommendationServiceImpl implements AutoRecommendationService {

    private static final int POOL_LIMIT = 10; // 후보 풀 최대 10개

    private final RequestRepository requestRepository;
    private final StationRepository stationRepository;
    private final RequestPlaceNotesQueryRepository notesQueryRepository;
    private final RequestDetailQueryRepository detailQueryRepository; // ★ B추천 제외/카테고리 수집용

    private final PlaceSearchService placeSearchService; // #5 검색 재사용
    private final AiSummaryService aiSummaryService;     // #9 요약 재사용
    private final AiKeywordService aiKeywordService;     // 신규: 키워드 추출
    private final ObjectMapper objectMapper;

    /**
     * q 파라미터는 무시합니다(요구사항).
     * - B가 이미 추천한 장소는 전부 제외.
     * - B가 추천한 장소들의 category_name 정규화 → 카테고리 키워드化.
     * - 요청메시지에서 키워드 최대 2개 추출.
     * - (카테고리 키워드 + 메시지 키워드)로 #5를 여러 번 호출해 풀(최대 10) 구성 → 제외목록 제거 → 상위 N 반환.
     */
    @Override
    public RequestDetailResponse recommend(String slug, Integer n, String qIgnored) {
        final int topN = clampN(n);

        // 요청/역
        Request req = requestRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "request not found"));
        Station st = stationRepository.findByCode(req.getStationCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "station not found"));

        // 0) B가 이미 추천한 장소 & 그 카테고리 수집 (한 번의 네이티브 조회로 해결)
        //    - 제외목록: external_id set
        //    - 카테고리: category_name set → 정규화
        List<RequestDetailQueryRepository.Row> existing = detailQueryRepository.findItemsBySlug(slug, 100);
        Set<String> excludeExternalIds = existing.stream()
                .map(RequestDetailQueryRepository.Row::getExternal_id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        LinkedHashSet<String> categoryKeywords = existing.stream()
                .map(RequestDetailQueryRepository.Row::getCategory_name)
                .filter(Objects::nonNull)
                .map(this::normalizeCategoryKeyword) // "음식점 > 카페 > ..." → "카페"/"식당"/"술집"/...
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 1) 요청 메시지에서 키워드 최대 2개 추출
        LinkedHashSet<String> messageKeywords = new LinkedHashSet<>();
        List<String> extracted = aiKeywordService.extractTop2(req.getRequestMessage());
        if (extracted != null) {
            for (String kw : extracted) {
                if (kw != null && !kw.isBlank()) messageKeywords.add(kw.trim());
            }
        }

        // 2) 최종 검색어 집합: [카테고리 키워드 + 메시지 키워드], 비었으면 백업 "카페"
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.addAll(categoryKeywords);
        queries.addAll(messageKeywords);
        if (queries.isEmpty()) queries.add("카페");

        // 3) 후보 수집(중복 제거: external_id 기준) — 최대 10개
        LinkedHashMap<String, PlaceDtos.Item> pool = new LinkedHashMap<>();
        for (String kw : queries) {
            PlaceDtos.SearchResponse resp = placeSearchService.search(st.getCode(), kw);
            if (resp == null || resp.items() == null) continue;
            for (PlaceDtos.Item it : resp.items()) {
                if (!pool.containsKey(it.external_id())) pool.put(it.external_id(), it);
                if (pool.size() >= POOL_LIMIT) break;
            }
            if (pool.size() >= POOL_LIMIT) break;
        }

        // 4) B가 이미 추천한 장소는 전부 제외(요구사항: 아예 제외)
        List<PlaceDtos.Item> candidates = pool.values().stream()
                .filter(it -> !excludeExternalIds.contains(it.external_id()))
                .toList();

        // 제외하고 나면 없을 수도 있음 → 빈 리스트 반환
        if (candidates.isEmpty()) {
            return new RequestDetailResponse(
                    req.getSlug(),
                    new RequestDetailDtos.Station(
                            st.getCode(), st.getName(), st.getLine(),
                            toBigDecimal(st.getLat()), toBigDecimal(st.getLng())
                    ),
                    req.getRequestMessage(),
                    List.of()
            );
        }

        // 5) 상위 N (클램프된 n) — 복잡한 재랭킹 없이 입력 순서 기준으로 컷
        List<PlaceDtos.Item> picked = candidates.stream().limit(topN).toList();

        // 6) evidence용 태그 집계 (#9와 동일 쿼리 재사용)
        String[] pickedExtIds = picked.stream().map(PlaceDtos.Item::external_id).toArray(String[]::new);
        Map<String, List<String>> userTagsMap = fetchUserTags(slug, pickedExtIds);

        // 7) #7 스키마로 매핑 + AI 요약(캐시 안쓰고 즉시 호출)
        List<Item> items = new ArrayList<>(picked.size());
        for (PlaceDtos.Item c : picked) {
            Mock mock = null;
            if (c.mock() != null) {
                mock = new Mock(
                        c.mock().rating(),
                        c.mock().rating_count(),
                        c.mock().image_urls(),
                        c.mock().opening_hours()
                );
            }
            List<String> reviewSnippets = (c.mock() != null) ? c.mock().review_snippets() : null;
            List<String> userTags = userTagsMap.get(c.external_id());

            Optional<String> summaryOpt = aiSummaryService.generateSummary(
                    c.place_name(),
                    c.category_name(),
                    (c.mock() != null) ? c.mock().rating() : null,
                    (c.mock() != null) ? c.mock().rating_count() : null,
                    reviewSnippets,
                    userTags
            );

            Ai ai = summaryOpt
                    .map(txt -> new Ai(
                            txt,
                            buildEvidence(c.category_name(),
                                    (c.mock() != null) ? c.mock().rating() : null,
                                    (c.mock() != null) ? c.mock().rating_count() : null,
                                    reviewSnippets,
                                    userTags),
                            OffsetDateTime.now()))
                    .orElse(null);

            // distance: 응답에 있으면 사용, 없으면 역 기준 계산
            Integer dist = c.distance_m();
            if (dist == null) {
                double d = haversineMeters(
                        toDouble(toBigDecimal(st.getLat())), toDouble(toBigDecimal(st.getLng())),
                        toDouble(c.y()), toDouble(c.x())
                );
                dist = (int) Math.round(d);
            }

            Item it = new Item(
                    c.external_id(),
                    c.id(),
                    c.place_name(),
                    c.category_group_code(),
                    c.category_group_name(),
                    c.category_name(),
                    c.address_name(),
                    c.road_address_name(),
                    c.x(),
                    c.y(),
                    dist,
                    c.place_url(),
                    mock,
                    ai,
                    null // 자동추천은 recommended_count 집계 없음
            );
            items.add(it);
        }

        return new RequestDetailResponse(
                req.getSlug(),
                new RequestDetailDtos.Station(
                        st.getCode(), st.getName(), st.getLine(),
                        toBigDecimal(st.getLat()), toBigDecimal(st.getLng())
                ),
                req.getRequestMessage(),
                items
        );
    }

    // ---------- helpers ----------

    private int clampN(Integer n) {
        if (n == null) return 1;
        return Math.min(Math.max(n, 1), 5);
    }

    private Map<String, List<String>> fetchUserTags(String slug, String[] externalIds) {
        if (externalIds.length == 0) return Map.of();
        return notesQueryRepository.findTagsAggByExternalIds(slug, externalIds).stream()
                .collect(Collectors.toMap(
                        RequestPlaceNotesQueryRepository.TagAgg::getExternal_id,
                        row -> parseJsonArrayOfString(row.getTags_json())
                ));
    }

    private List<String> parseJsonArrayOfString(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> buildEvidence(String categoryName, Double rating, Integer ratingCount,
                                              List<String> reviewSnippets, List<String> userTags) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("category_name", categoryName);
        if (rating != null) ev.put("rating", rating);
        if (ratingCount != null) ev.put("rating_count", ratingCount);
        if (reviewSnippets != null && !reviewSnippets.isEmpty()) ev.put("review_snippets", reviewSnippets);
        if (userTags != null && !userTags.isEmpty()) ev.put("user_tags", userTags);
        return ev;
    }

    /**
     * "음식점 > 카페 > 커피전문점 > 스타벅스" 같은 문자열을
     * 의미 있는 카테고리 키워드로 정규화.
     */
    private String normalizeCategoryKeyword(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) return null;
        String[] parts = Arrays.stream(categoryName.split(">"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (parts.length == 0) return null;

        // 하위에서 상위로 올라가며 카테고리성 키워드 탐색
        for (int i = parts.length - 1; i >= 0; i--) {
            String p = parts[i];
            if (p.contains("카페")) return "카페";
            if (p.contains("바") || p.contains("술") || p.contains("맥주")) return "술집";
            if (p.contains("식당") || p.contains("한식") || p.contains("일식")
                    || p.contains("중식") || p.contains("양식") || p.contains("분식")) return "식당";
            if (p.contains("베이커리") || p.contains("빵")) return "베이커리";
            if (p.contains("디저트")) return "디저트";
        }
        // 카테고리 단어가 없으면 그냥 리프 반환(최후 수단)
        return parts[parts.length - 1];
    }

    private double toDouble(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try { return Double.parseDouble(s); } catch (Exception ignore) { return 0.0; }
    }
    private double toDouble(BigDecimal bd) { return (bd == null) ? 0.0 : bd.doubleValue(); }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat/2),2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon/2),2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return R * c;
    }
}
