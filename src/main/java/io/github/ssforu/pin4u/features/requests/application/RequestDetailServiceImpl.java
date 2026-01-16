package io.github.ssforu.pin4u.features.requests.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ssforu.pin4u.features.places.application.MockAllocator;
import io.github.ssforu.pin4u.features.places.domain.PlaceMock;
import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos.*;
import io.github.ssforu.pin4u.features.requests.infra.RequestDetailQueryRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestDetailQueryRepository.Row;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceNotesQueryRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.stations.domain.Station;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
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
    private final MockAllocator mockAllocator;

    public RequestDetailServiceImpl(
            RequestRepository requestRepository,
            StationRepository stationRepository,
            RequestDetailQueryRepository queryRepository,
            RequestPlaceNotesQueryRepository notesQueryRepository,
            AiSummaryService aiSummaryService,
            ObjectMapper objectMapper,
            MockAllocator mockAllocator
    ) {
        this.requestRepository = requestRepository;
        this.stationRepository = stationRepository;
        this.queryRepository = queryRepository;
        this.notesQueryRepository = notesQueryRepository;
        this.aiSummaryService = aiSummaryService;
        this.objectMapper = objectMapper;
        this.mockAllocator = mockAllocator;
    }

    @Override
    @Transactional
    public RequestDetailResponse getRequestDetail(String slug, Integer limit, boolean includeAi) {
        Request req = requestRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "request not found"));
        Station st = stationRepository.findByCode(req.getStationCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "station not found"));

        int lim = (limit == null) ? 12 : Math.min(Math.max(limit, 1), 50);

        List<Row> rows = queryRepository.findItemsBySlug(slug, lim);

        List<Item> items = new ArrayList<>(rows.size());
        for (Row r : rows) {
            Mock mock = null;
            if (r.getMock_rating() != null || r.getMock_rating_count() != null
                    || r.getMock_image_urls_json() != null || r.getMock_opening_hours_json() != null) {
                mock = new Mock(
                        r.getMock_rating(),
                        r.getMock_rating_count(),
                        parseJsonArrayOfString(r.getMock_image_urls_json()),
                        parseJsonArrayOfString(r.getMock_opening_hours_json())
                );
            }

            items.add(new Item(
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
                    null,
                    r.getRecommended_count()
            ));
        }

        Set<String> need = items.stream()
                .filter(it -> it.mock() == null)
                .map(Item::externalId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!need.isEmpty()) {
            Map<String, PlaceMock> ensured = mockAllocator.ensureMocks(need);
            // ★ 수정됨: .toList() 대신 Collectors.toList()를 사용하여 수정 가능한 리스트로 반환
            items = items.stream().map(cur -> {
                if (cur.mock() != null) return cur;
                PlaceMock pm = ensured.get(cur.externalId());
                if (pm == null) return cur;
                Mock filled = new Mock(
                        pm.getRating() == null ? null : pm.getRating().doubleValue(),
                        pm.getRatingCount(),
                        parseJsonArrayOfString(pm.getImageUrlsJson()),
                        parseJsonArrayOfString(pm.getOpeningHoursJson())
                );
                return new Item(
                        cur.externalId(), cur.id(), cur.placeName(),
                        cur.categoryGroupCode(), cur.categoryGroupName(), cur.categoryName(),
                        cur.addressName(), cur.roadAddressName(),
                        cur.x(), cur.y(), cur.distanceM(), cur.placeUrl(),
                        filled, cur.ai(), cur.recommendedCount()
                );
            }).collect(Collectors.toList());
        } else {
            // ★ 수정됨: 아이템이 이미 다 있어도 수정 가능하도록 ArrayList로 감싸기
            items = new ArrayList<>(items);
        }

        Map<String, List<String>> reviewSnippetsMap = new HashMap<>();
        for (Row r : rows) {
            List<String> rs = parseJsonArrayOfString(r.getMock_review_snippets_json());
            if (rs != null && !rs.isEmpty()) {
                reviewSnippetsMap.put(r.getExternal_id(), rs);
            }
        }

        String[] externalIds = items.stream().map(Item::externalId).distinct().toArray(String[]::new);
        Map<String, List<String>> userTagsMap = notesQueryTags(slug, externalIds);

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
                String evidence = toEvidenceJson200(Map.of(
                        "category_name", cur.categoryName(),
                        "rating", rating,
                        "rating_count", ratingCount,
                        "review_snippets", (reviewSnippets == null ? List.of() : reviewSnippets),
                        "user_tags", (userTags == null ? List.of() : userTags)
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

        return new RequestDetailResponse(req.getSlug(), dtoStation, req.getRequestMessage(), items, null);
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
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }

    private String toEvidenceJson200(Map<String, ?> ev) {
        try {
            String s = objectMapper.writeValueAsString(ev);
            return (s.length() <= 200) ? s : s.substring(0, 200);
        } catch (Exception e) {
            return null;
        }
    }
}