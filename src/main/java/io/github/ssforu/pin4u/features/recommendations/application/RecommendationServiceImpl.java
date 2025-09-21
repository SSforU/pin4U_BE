package io.github.ssforu.pin4u.features.recommendations.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ssforu.pin4u.features.places.domain.Place;
import io.github.ssforu.pin4u.features.places.infra.PlaceRepository;
import io.github.ssforu.pin4u.features.recommendations.domain.RecommendationNote;
import io.github.ssforu.pin4u.features.recommendations.dto.RecommendationDtos;
import io.github.ssforu.pin4u.features.recommendations.infra.RecommendationNoteRepository;
import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.requests.domain.RequestPlaceAggregate;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceAggregateRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.stations.domain.Station;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "분위기 맛집","핫플","힐링 스팟","또간집","숨은 맛집","가성비 갑"
    );

    private final RequestRepository requestRepository;
    private final StationRepository stationRepository;
    private final PlaceRepository placeRepository;
    private final RequestPlaceAggregateRepository aggregateRepository;
    private final RecommendationNoteRepository noteRepository;
    private final ObjectMapper om;
    private final int stationRadiusM;

    public RecommendationServiceImpl(
            RequestRepository requestRepository,
            StationRepository stationRepository,
            PlaceRepository placeRepository,
            RequestPlaceAggregateRepository aggregateRepository,
            RecommendationNoteRepository noteRepository,
            ObjectMapper om,
            @Value("${app.search.stationRadiusM:1500}") int stationRadiusM
    ) {
        this.requestRepository = requestRepository;
        this.stationRepository = stationRepository;
        this.placeRepository = placeRepository;
        this.aggregateRepository = aggregateRepository;
        this.noteRepository = noteRepository;
        this.om = om;
        this.stationRadiusM = stationRadiusM;
    }

    @Override
    @Transactional
    public RecommendationDtos.SubmitResponse submit(String slug, RecommendationDtos.SubmitRequest req) {
        Request request = requestRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("request_not_found"));

        Station st = stationRepository.findByCode(request.getStationCode())
                .orElseThrow(() -> new NoSuchElementException("station_not_found"));
        BigDecimal lat = st.getLat();
        BigDecimal lng = st.getLng();

        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            return emptyResponse();
        }

        // 1) 외부ID 후보 확장 (원문 + 정규화 후보)
        Set<String> expandedExtIds = new LinkedHashSet<>();
        for (RecommendationDtos.SubmitItem it : req.getItems()) {
            String raw = safeTrim(it.getExternalId());
            if (raw == null) continue;
            expandedExtIds.add(raw);
            for (String cand : normalizeCandidates(raw)) {
                expandedExtIds.add(cand);
            }
        }

        // 2) 확장된 후보로 일괄 조회 (키 → Place 맵)
        Map<String, Place> placeByExt = placeRepository.findByExternalIdIn(new ArrayList<>(expandedExtIds)).stream()
                .collect(Collectors.toMap(Place::getExternalId, p -> p, (a, b) -> a));

        RecommendationDtos.SubmitResponse out = new RecommendationDtos.SubmitResponse();
        int saved = 0, conflicts = 0, outOfRadius = 0, notFound = 0, invalid = 0;

        for (RecommendationDtos.SubmitItem it : req.getItems()) {
            String externalId = safeTrim(it.getExternalId());
            Map<String, String> invalidDetails = new LinkedHashMap<>();

            // 3) 검증 완화: null/blank만 거부 + 허용 패턴 최소 점검
            if (externalId == null || externalId.isBlank()) {
                invalidDetails.put("external_id", "required");
            } else if (!looksLikeSupportedExternalId(externalId)) {
                invalidDetails.put("external_id", "invalid_format");
            }

            String nickname = safeTrim(it.getRecommenderNickname());
            if (nickname == null || nickname.length() < 2 || nickname.length() > 16) {
                invalidDetails.put("recommender_nickname", "length_2_to_16");
            }
            String msg = it.getRecommendMessage();
            if (msg != null && msg.length() > 300) {
                invalidDetails.put("recommend_message", "max_length_300");
            }
            String img = it.getImageUrl();
            if (img != null && img.length() > 300) {
                invalidDetails.put("image_url", "max_length_300");
            }
            UUID guestUuid = parseUuid(it.getGuestId());
            if (guestUuid == null) {
                invalidDetails.put("guest_id", "invalid_uuid");
            }
            List<String> rawTags = it.getTags();
            List<String> tags = sanitizeTags(rawTags);
            if (rawTags != null) {
                if (rawTags.size() > 3) invalidDetails.put("tags", "max_size_3");
                if (!allAllowed(rawTags)) invalidDetails.put("tags", "invalid_value");
            }

            if (!invalidDetails.isEmpty()) {
                out.getInvalid().add(new RecommendationDtos.InvalidItem(externalId, invalidDetails));
                invalid++;
                continue;
            }

            // 4) 매칭: 원문 → 후보 순서로 Place 찾기
            Place place = null;
            if (externalId != null) {
                place = placeByExt.get(externalId);
                if (place == null) {
                    for (String cand : normalizeCandidates(externalId)) {
                        place = placeByExt.get(cand);
                        if (place != null) break;
                    }
                }
            }

            if (place == null) {
                out.getNotFound().add(new RecommendationDtos.SimpleItem(externalId));
                notFound++;
                continue;
            }

            Integer distM = safeDistanceM(lat, lng, place.getY(), place.getX());
            if (distM == null || distM > stationRadiusM) {
                out.getOutOfRadius().add(new RecommendationDtos.OutOfRadiusItem(place.getExternalId(), distM));
                outOfRadius++;
                continue;
            }

            Long placeId = place.getId();
            RequestPlaceAggregate agg = aggregateRepository
                    .findByRequestIdAndPlaceId(slug, placeId)
                    .orElseGet(() -> new RequestPlaceAggregate(slug, placeId));

            if (agg.getId() != null && noteRepository.existsByRpaIdAndGuestId(agg.getId(), guestUuid)) {
                out.getConflicts().add(new RecommendationDtos.SimpleItem(place.getExternalId()));
                conflicts++;
                continue;
            }

            agg.increaseCount();
            agg = aggregateRepository.save(agg);

            boolean imageIsPublic = (it.getImageIsPublic() == null) ? true : it.getImageIsPublic();

            RecommendationNote note = new RecommendationNote(
                    agg.getId(),
                    nickname,
                    msg,
                    img,
                    imageIsPublic,
                    tags,
                    guestUuid,
                    OffsetDateTime.now()
            );
            noteRepository.save(note);

            out.getSaved().add(new RecommendationDtos.SavedItem(place.getExternalId(), agg.getRecommendedCount()));
            saved++;
        }

        Map<String, Integer> totals = out.getTotals();
        totals.put("saved", saved);
        totals.put("conflicts", conflicts);
        totals.put("out_of_radius", outOfRadius);
        totals.put("not_found", notFound);
        totals.put("invalid", invalid);

        return out;
    }

    private RecommendationDtos.SubmitResponse emptyResponse() {
        RecommendationDtos.SubmitResponse r = new RecommendationDtos.SubmitResponse();
        r.getTotals().put("saved", 0);
        r.getTotals().put("conflicts", 0);
        r.getTotals().put("out_of_radius", 0);
        r.getTotals().put("not_found", 0);
        r.getTotals().put("invalid", 0);
        return r;
    }

    private static String safeTrim(String s) { return (s == null) ? null : s.trim(); }

    /** 우리가 허용하는 외부 ID 패턴인지 최소한만 점검 */
    private static boolean looksLikeSupportedExternalId(String id) {
        if (id == null || id.isBlank()) return false;
        // 허용: kakao:..., mock:..., mock-...
        if (id.startsWith("kakao:")) return true;
        if (id.startsWith("mock:")) return true;
        if (id.startsWith("mock-")) return true;
        return false;
    }

    /** DB 조회용 후보 생성: mock:001 <-> mock-001 양방향을 모두 커버 */
    private static List<String> normalizeCandidates(String raw) {
        if (raw == null) return List.of();
        List<String> out = new ArrayList<>();
        if (raw.startsWith("mock:")) {
            out.add("mock-" + raw.substring("mock:".length()));
        } else if (raw.startsWith("mock-")) {
            out.add("mock:" + raw.substring("mock-".length()));
        }
        // 필요 시 kakao:123 <-> 123도 지원하려면 여기에 추가
        return out;
    }

    private boolean allAllowed(List<String> tags) {
        if (tags == null) return true;
        for (String t : tags) {
            if (t == null) return false;
            if (!ALLOWED_TAGS.contains(t.trim())) return false;
        }
        return true;
    }

    private List<String> sanitizeTags(List<String> tags) {
        if (tags == null) return null;
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(ALLOWED_TAGS::contains)
                .limit(3)
                .toList();
    }

    private UUID parseUuid(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    private static Double parseCoord(Object v) {
        if (v == null) return null;
        try {
            if (v instanceof java.math.BigDecimal bd) return bd.doubleValue();
            if (v instanceof Number n) return n.doubleValue();
            return Double.parseDouble(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer safeDistanceM(BigDecimal lat, BigDecimal lng, Object placeY, Object placeX) {
        try {
            Double y = parseCoord(placeY);
            Double x = parseCoord(placeX);
            if (y == null || x == null) return null;
            return haversineM(lat.doubleValue(), lng.doubleValue(), y, x);
        } catch (Exception e) {
            return null;
        }
    }

    private int haversineM(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000.0;
        double φ1 = Math.toRadians(lat1);
        double φ2 = Math.toRadians(lat2);
        double dφ = Math.toRadians(lat2 - lat1);
        double dλ = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dφ/2) * Math.sin(dφ/2)
                + Math.cos(φ1) * Math.cos(φ2) * Math.sin(dλ/2) * Math.sin(dλ/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(R * c);
    }
}
