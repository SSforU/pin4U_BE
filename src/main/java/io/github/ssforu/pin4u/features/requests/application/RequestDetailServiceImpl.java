package io.github.ssforu.pin4u.features.requests.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos.*;
import io.github.ssforu.pin4u.features.requests.infra.RequestDetailQueryRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestDetailQueryRepository.Row;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceNotesQueryRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.stations.domain.Station;
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
public class RequestDetailServiceImpl implements RequestDetailService {

    private final RequestRepository requestRepository;
    private final StationRepository stationRepository;
    private final RequestDetailQueryRepository queryRepository;
    private final RequestPlaceNotesQueryRepository notesQueryRepository;
    private final AiSummaryService aiSummaryService;
    private final ObjectMapper objectMapper;

    public RequestDetailServiceImpl(
            RequestRepository requestRepository,
            StationRepository stationRepository,
            RequestDetailQueryRepository queryRepository,
            RequestPlaceNotesQueryRepository notesQueryRepository,
            AiSummaryService aiSummaryService,
            ObjectMapper objectMapper
    ) {
        this.requestRepository = requestRepository;
        this.stationRepository = stationRepository;
        this.queryRepository = queryRepository;
        this.notesQueryRepository = notesQueryRepository;
        this.aiSummaryService = aiSummaryService;
        this.objectMapper = objectMapper;
    }

    @Override
    public RequestDetailResponse getRequestDetail(String slug, Integer limit, boolean includeAi /* ignored: always try */) {
        // 1) 요청/역 조회
        Request req = requestRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "request not found"));
        Station st = stationRepository.findByCode(req.getStationCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "station not found"));

        // 2) limit 클램프(기본 12, 1~50)
        int lim = (limit == null) ? 12 : Math.min(Math.max(limit, 1), 50);

        // 3) 목록 조회
        List<Row> rows = queryRepository.findItemsBySlug(slug, lim);

        // 4) Row -> Item 1차 매핑 (mock 우선)
        List<Item> items = new ArrayList<>(rows.size());
        for (Row r : rows) {
            Mock mock = null;
            if (r.getMock_rating() != null || r.getMock_rating_count() != null
                    || r.getMock_image_urls_json() != null || r.getMock_opening_hours_json() != null) {
                List<String> imageUrls = parseJsonArrayOfString(r.getMock_image_urls_json());
                List<String> openingHours = parseJsonArrayOfString(r.getMock_opening_hours_json());
                mock = new Mock(
                        r.getMock_rating(),
                        r.getMock_rating_count(),
                        imageUrls,
                        openingHours
                );
            }

            Item item = new Item(
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
                    null, // ai는 아래에서 채움
                    r.getRecommended_count()
            );
            items.add(item);
        }

        // 5) evidence 준비: review_snippets(from place_mock) + user_tags(추천노트 집계)
        Map<String, List<String>> reviewSnippetsMap = new HashMap<>();
        for (Row r : rows) {
            List<String> rs = parseJsonArrayOfString(r.getMock_review_snippets_json());
            if (rs != null && !rs.isEmpty()) {
                reviewSnippetsMap.put(r.getExternal_id(), rs);
            }
        }

        String[] externalIds = items.stream()
                .map(Item::externalId)
                .distinct()
                .toArray(String[]::new);
        Map<String, List<String>> userTagsMap = notesQueryTags(slug, externalIds);

        // 6) AI 요약 시도
        ListIterator<Item> it = items.listIterator();
        while (it.hasNext()) {
            Item cur = it.next();

            List<String> reviewSnippets = reviewSnippetsMap.get(cur.externalId());
            List<String> userTags = userTagsMap.get(cur.externalId());

            Double rating = (cur.mock() == null) ? null : cur.mock().rating();
            Integer ratingCount = (cur.mock() == null) ? null : cur.mock().ratingCount();

            Optional<String> summaryOpt = aiSummaryService.generateSummary(
                    cur.placeName(), cur.categoryName(), rating, ratingCount, reviewSnippets, userTags
            );

            if (summaryOpt.isPresent()) {
                // evidence를 JSON compact string으로 만들고 200자 제한
                String evidence = toEvidenceJson200(Map.of(
                        "category_name", cur.categoryName(),
                        "rating",        rating,
                        "rating_count",  ratingCount,
                        "review_snippets", (reviewSnippets == null ? List.of() : reviewSnippets),
                        "user_tags",       (userTags == null ? List.of() : userTags)
                ));

                Ai ai = new Ai(summaryOpt.get(), evidence, OffsetDateTime.now());

                it.set(new Item(
                        cur.externalId(), cur.id(), cur.placeName(),
                        cur.categoryGroupCode(), cur.categoryGroupName(), cur.categoryName(),
                        cur.addressName(), cur.roadAddressName(),
                        cur.x(), cur.y(), cur.distanceM(), cur.placeUrl(),
                        cur.mock(), ai, cur.recommendedCount()
                ));
            }
        }

        RequestDetailDtos.Station dtoStation = new RequestDetailDtos.Station(
                st.getCode(), st.getName(), st.getLine(), toBigDecimal(st.getLat()), toBigDecimal(st.getLng())
        );

        return new RequestDetailResponse(req.getSlug(), dtoStation, req.getRequestMessage(), items);
    }

    private Map<String, List<String>> notesQueryTags(String slug, String[] externalIds) {
        if (externalIds == null || externalIds.length == 0) return Map.of();
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
            return null; // 파싱 실패 허용
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }

    /** evidence JSON 문자열을 200자 이내로 안전하게 축약 */
    private String toEvidenceJson200(Map<String, ?> ev) {
        try {
            String s = objectMapper.writeValueAsString(ev);
            return (s.length() <= 200) ? s : s.substring(0, 200);
        } catch (Exception e) {
            return null;
        }
    }
}
