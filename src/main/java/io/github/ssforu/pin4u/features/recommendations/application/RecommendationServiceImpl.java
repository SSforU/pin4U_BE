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
    private final ObjectMapper om; // 현재는 미사용(향후 로깅/디버깅에 유용)
    private final int stationRadiusM;

    public RecommendationServiceImpl(
            RequestRepository requestRepository,
            StationRepository stationRepository,
            PlaceRepository placeRepository,
            RequestPlaceAggregateRepository aggregateRepository,
            RecommendationNoteRepository noteRepository,
            ObjectMapper om,
            @Value("${app.search.stationRadiusM:800}") int stationRadiusM
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
        // 0) slug 검증
        Request request = requestRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("request_not_found"));

        // 1) 역 좌표
        Station st = stationRepository.findByCode(request.getStationCode())
                .orElseThrow(() -> new NoSuchElementException("station_not_found"));
        BigDecimal lat = st.getLat();
        BigDecimal lng = st.getLng();

        // 2) 입력 검증(최상위)
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            return emptyResponse();
        }

        // 3) external_id → Place 일괄 조회 (기존 로직 유지)
        List<String> extIds = req.getItems().stream()
                .map(RecommendationDtos.SubmitItem::getExternalId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .toList();

        Map<String, Place> placeByExt = placeRepository.findByExternalIdIn(extIds).stream()
                .collect(Collectors.toMap(Place::getExternalId, p -> p, (a, b) -> a));

        // 4) 결과 버킷
        RecommendationDtos.SubmitResponse out = new RecommendationDtos.SubmitResponse();
        int saved = 0, conflicts = 0, outOfRadius = 0, notFound = 0, invalid = 0;

        for (RecommendationDtos.SubmitItem it : req.getItems()) {
            String externalId = safeTrim(it.getExternalId());
            Map<String, String> invalidDetails = new LinkedHashMap<>();

            // 4-1) 필드 유효성 (기존 로직 유지)
            if (externalId == null || !(externalId.startsWith("kakao:") || externalId.startsWith("mock:"))) {
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
            if (img != null && img.length() > 1000) {
                invalidDetails.put("image_url", "max_length_1000");
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

            // 4-2) 장소 존재성 (기존 로직 유지)
            Place place = placeByExt.get(externalId);
            if (place == null) {
                out.getNotFound().add(new RecommendationDtos.SimpleItem(externalId));
                notFound++;
                continue;
            }

            // 4-3) 반경 검증(역 lat/lng vs place y/x)
            Integer distM = safeDistanceM(lat, lng, place.getY(), place.getX());
            if (distM == null || distM > stationRadiusM) {
                out.getOutOfRadius().add(new RecommendationDtos.OutOfRadiusItem(externalId, distM));
                outOfRadius++;
                continue;
            }

            // 4-4) ★ 핵심: externalId(String) → placeId(Long)로 변환 후 집계 조회/생성
            Long placeId = place.getId(); // [FIX] 정규화된 FK(places.id) 사용 — ERD 및 Flyway와 일치시킴
            RequestPlaceAggregate agg = aggregateRepository
                    .findByRequestIdAndPlaceId(slug, placeId) // [FIX] 레포 시그니처도 placeId로 통일
                    .orElseGet(() -> new RequestPlaceAggregate(slug, placeId)); // [FIX] 엔티티 생성자도 (slug, placeId)

            // 4-5) 동일 guest 중복 방지 (기존 로직 유지)
            if (agg.getId() != null && noteRepository.existsByRpaIdAndGuestId(agg.getId(), guestUuid)) {
                out.getConflicts().add(new RecommendationDtos.SimpleItem(externalId));
                conflicts++;
                continue;
            }

            // 4-6) 집계 증가 + 저장 (기존 로직 유지)
            agg.increaseCount();
            agg = aggregateRepository.save(agg);

            // 4-7) 노트 저장 (기존 로직 유지)
            RecommendationNote note = new RecommendationNote(
                    agg.getId(),               // rpaId
                    nickname,                  // nickname
                    msg,                       // recommendMessage
                    img,                       // imageUrl
                    tags,                      // tags(JSONB)
                    guestUuid,                 // guestId
                    OffsetDateTime.now()       // createdAt
            );
            noteRepository.save(note);

            // 4-8) 응답(saved) 작성 (기존 로직 유지)
            out.getSaved().add(new RecommendationDtos.SavedItem(externalId, agg.getRecommendedCount()));
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

    // ====== 보조 메서드들 (그대로) ======

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

    // 문자열/숫자(BigDecimal 포함) 모두 허용하는 안전 파서
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
            Double y = parseCoord(placeY); // Kakao: y = lat
            Double x = parseCoord(placeX); // Kakao: x = lng
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



/* 3차
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

// [CLEANUP] EntityManager 주입/JPQL 대체 로직 제거 (레포 시그니처 정합화로 불필요)
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
    private final ObjectMapper om; // 현재는 미사용(향후 로깅/디버깅에 유용)
    private final int stationRadiusM;

    public RecommendationServiceImpl(
            RequestRepository requestRepository,
            StationRepository stationRepository,
            PlaceRepository placeRepository,
            RequestPlaceAggregateRepository aggregateRepository,
            RecommendationNoteRepository noteRepository,
            ObjectMapper om,
            @Value("${app.search.stationRadiusM:800}") int stationRadiusM
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
        // 0) slug 검증
        Request request = requestRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("request_not_found"));

        // 1) 역 좌표
        Station st = stationRepository.findByCode(request.getStationCode())
                .orElseThrow(() -> new NoSuchElementException("station_not_found"));
        BigDecimal lat = st.getLat();
        BigDecimal lng = st.getLng();

        // 2) 입력 검증(최상위)
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            return emptyResponse();
        }

        // 3) external_id → Place 일괄 조회 (기존 로직 유지)
        List<String> extIds = req.getItems().stream()
                .map(RecommendationDtos.SubmitItem::getExternalId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .toList();

        Map<String, Place> placeByExt = placeRepository.findByExternalIdIn(extIds).stream()
                .collect(Collectors.toMap(Place::getExternalId, p -> p, (a, b) -> a));

        // 4) 결과 버킷
        RecommendationDtos.SubmitResponse out = new RecommendationDtos.SubmitResponse();
        int saved = 0, conflicts = 0, outOfRadius = 0, notFound = 0, invalid = 0;

        for (RecommendationDtos.SubmitItem it : req.getItems()) {
            String externalId = safeTrim(it.getExternalId());
            Map<String, String> invalidDetails = new LinkedHashMap<>();

            // 4-1) 필드 유효성 (기존 로직 유지)
            if (externalId == null || !(externalId.startsWith("kakao:") || externalId.startsWith("mock:"))) {
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
            if (img != null && img.length() > 1000) {
                invalidDetails.put("image_url", "max_length_1000");
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

            // 4-2) 장소 존재성 (기존 로직 유지)
            Place place = placeByExt.get(externalId);
            if (place == null) {
                out.getNotFound().add(new RecommendationDtos.SimpleItem(externalId));
                notFound++;
                continue;
            }

            // 4-3) 반경 검증(역 lat/lng vs place y/x)
            Integer distM = safeDistanceM(lat, lng, place.getY(), place.getX());
            if (distM == null || distM > stationRadiusM) {
                out.getOutOfRadius().add(new RecommendationDtos.OutOfRadiusItem(externalId, distM));
                outOfRadius++;
                continue;
            }

            // 4-4) ★ 핵심: externalId(String) → placeId(Long)로 변환 후 집계 조회/생성
            Long placeId = place.getId(); // [FIX] 내부 조인/집계는 places.id 사용(DDL/FK 일치)
            RequestPlaceAggregate agg = aggregateRepository
                    .findByRequestIdAndPlaceId(slug, placeId) // [FIX] 레포 시그니처 정합
                    .orElseGet(() -> new RequestPlaceAggregate(slug, placeId)); // [FIX] 엔티티 생성자 (slug, placeId)

            // 4-5) 동일 guest 중복 방지 (기존 로직 유지)
            if (agg.getId() != null && noteRepository.existsByRpaIdAndGuestId(agg.getId(), guestUuid)) {
                out.getConflicts().add(new RecommendationDtos.SimpleItem(externalId));
                conflicts++;
                continue;
            }

            // 4-6) 집계 증가 + 저장 (기존 로직 유지)
            agg.increaseCount();
            agg = aggregateRepository.save(agg);

            // 4-7) 노트 저장 (기존 로직 유지)
            RecommendationNote note = new RecommendationNote(
                    agg.getId(),               // rpaId
                    nickname,                  // nickname
                    msg,                       // recommendMessage
                    img,                       // imageUrl
                    tags,                      // tags(JSONB)
                    guestUuid,                 // guestId
                    OffsetDateTime.now()       // createdAt
            );
            noteRepository.save(note);

            // 4-8) 응답(saved) 작성 (기존 로직 유지)
            out.getSaved().add(new RecommendationDtos.SavedItem(externalId, agg.getRecommendedCount()));
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

    // ====== 보조 메서드들 (그대로) ======

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

    // 문자열/숫자(BigDecimal 포함) 모두 허용하는 안전 파서
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
            Double y = parseCoord(placeY); // Kakao: y = lat
            Double x = parseCoord(placeX); // Kakao: x = lng
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
*/


/* 2차
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

import jakarta.persistence.EntityManager;           // [FIX] JPQL 단건 조회용 (리포지토리 변경 회피)
import jakarta.persistence.PersistenceContext;    // [FIX]
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
    private final ObjectMapper om; // 현재는 미사용(향후 로깅/디버깅에 유용)
    private final int stationRadiusM;

    @PersistenceContext
    private EntityManager em; // [FIX] 리포지토리 인터페이스 변경 없이 RPA 단건 조회하려고 주입

    public RecommendationServiceImpl(
            RequestRepository requestRepository,
            StationRepository stationRepository,
            PlaceRepository placeRepository,
            RequestPlaceAggregateRepository aggregateRepository,
            RecommendationNoteRepository noteRepository,
            ObjectMapper om,
            @Value("${app.search.stationRadiusM:800}") int stationRadiusM
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
        // 0) slug 검증
        Request request = requestRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("request_not_found"));

        // 1) 역 좌표
        Station st = stationRepository.findByCode(request.getStationCode())
                .orElseThrow(() -> new NoSuchElementException("station_not_found"));
        BigDecimal lat = st.getLat();
        BigDecimal lng = st.getLng();

        // 2) 입력 검증(최상위)
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            return emptyResponse();
        }

        // 3) external_id → Place 일괄 조회 (기존 로직 유지)
        List<String> extIds = req.getItems().stream()
                .map(RecommendationDtos.SubmitItem::getExternalId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .toList();

        Map<String, Place> placeByExt = placeRepository.findByExternalIdIn(extIds).stream()
                .collect(Collectors.toMap(Place::getExternalId, p -> p, (a, b) -> a));

        // 4) 결과 버킷
        RecommendationDtos.SubmitResponse out = new RecommendationDtos.SubmitResponse();
        int saved = 0, conflicts = 0, outOfRadius = 0, notFound = 0, invalid = 0;

        for (RecommendationDtos.SubmitItem it : req.getItems()) {
            String externalId = safeTrim(it.getExternalId());
            Map<String, String> invalidDetails = new LinkedHashMap<>();

            // 4-1) 필드 유효성 (기존 로직 유지)
            if (externalId == null || !(externalId.startsWith("kakao:") || externalId.startsWith("mock:"))) {
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
            if (img != null && img.length() > 1000) {
                invalidDetails.put("image_url", "max_length_1000");
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

            // 4-2) 장소 존재성 (기존 로직 유지)
            Place place = placeByExt.get(externalId);
            if (place == null) {
                out.getNotFound().add(new RecommendationDtos.SimpleItem(externalId));
                notFound++;
                continue;
            }

            // 4-3) 반경 검증(역 lat/lng vs place y/x) — place.getY()/getX()는 문자열이지만 parseCoord로 안전 처리
            Integer distM = safeDistanceM(lat, lng, place.getY(), place.getX());
            if (distM == null || distM > stationRadiusM) {
                out.getOutOfRadius().add(new RecommendationDtos.OutOfRadiusItem(externalId, distM));
                outOfRadius++;
                continue;
            }

            // 4-4) ★ 핵심 수정: externalId(String) → placeId(Long)로 변환 후 집계 조회/생성
            Long placeId = place.getId(); // [FIX] DB 정규화에 맞춰 places.id 사용(합의사항)
            RequestPlaceAggregate agg = findRpaByRequestIdAndPlaceId(slug, placeId); // [FIX] 리포지토리 변경 없이 JPQL로 단건 조회
            if (agg == null) {
                agg = new RequestPlaceAggregate(slug, placeId); // [FIX] 엔티티 생성자 시그니처 변경에 맞춤
            }

            // 4-5) 동일 guest 중복 방지 (기존 로직 유지)
            if (agg.getId() != null && noteRepository.existsByRpaIdAndGuestId(agg.getId(), guestUuid)) {
                out.getConflicts().add(new RecommendationDtos.SimpleItem(externalId));
                conflicts++;
                continue;
            }

            // 4-6) 집계 증가 + 저장 (기존 로직 유지)
            agg.increaseCount();
            agg = aggregateRepository.save(agg);

            // 4-7) 노트 저장 (기존 로직 유지)
            RecommendationNote note = new RecommendationNote(
                    agg.getId(),               // rpaId
                    nickname,                  // nickname
                    msg,                       // recommendMessage
                    img,                       // imageUrl
                    tags,                      // tags(JSONB)
                    guestUuid,                 // guestId
                    OffsetDateTime.now()       // createdAt
            );
            noteRepository.save(note);

            // 4-8) 응답(saved) 작성 (기존 로직 유지)
            out.getSaved().add(new RecommendationDtos.SavedItem(externalId, agg.getRecommendedCount()));
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

    // ====== 아래는 기존 보조 메서드들 (그대로) ======

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

    // 문자열/숫자(BigDecimal 포함) 모두 허용하는 안전 파서
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
            Double y = parseCoord(placeY); // Kakao: y = lat
            Double x = parseCoord(placeX); // Kakao: x = lng
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

    // ====== [FIX] 리포지토리 시그니처는 그대로 두고, 서비스 내부 JPQL로 단건 조회 ======
    private RequestPlaceAggregate findRpaByRequestIdAndPlaceId(String requestId, Long placeId) {
        // [이유] RequestPlaceAggregate 엔티티가 placeId(Long)로 바뀌었으므로,
        //        기존 "findByRequestIdAndPlaceExternalId" 시그니처는 런타임 파싱 위험.
        //        레포 인터페이스를 건드리지 않기 위해 서비스 내부에서 JPQL 단건 조회로 대체.
        return em.createQuery(
                        "SELECT r FROM RequestPlaceAggregate r WHERE r.requestId = :rid AND r.placeId = :pid",
                        RequestPlaceAggregate.class)
                .setParameter("rid", requestId)
                .setParameter("pid", placeId)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}*/



/* 초안
// src/main/java/io/github/ssforu/pin4u/features/recommendations/application/RecommendationServiceImpl.java
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

import jakarta.persistence.EntityManager;           // [FIX] JPQL 단건 조회용 (리포지토리 변경 회피)
import jakarta.persistence.PersistenceContext;
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
    private final ObjectMapper om; // 현재는 미사용(향후 로깅/디버깅에 유용)
    private final int stationRadiusM;

    public RecommendationServiceImpl(
            RequestRepository requestRepository,
            StationRepository stationRepository,
            PlaceRepository placeRepository,
            RequestPlaceAggregateRepository aggregateRepository,
            RecommendationNoteRepository noteRepository,
            ObjectMapper om,
            @Value("${app.search.stationRadiusM:800}") int stationRadiusM
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
        // 0) slug 검증
        Request request = requestRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("request_not_found"));

        // 1) 역 좌표
        Station st = stationRepository.findByCode(request.getStationCode())
                .orElseThrow(() -> new NoSuchElementException("station_not_found"));
        BigDecimal lat = st.getLat();
        BigDecimal lng = st.getLng();

        // 2) 입력 검증(최상위)
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            return emptyResponse();
        }

        // 3) external_id → Place 일괄 조회
        List<String> extIds = req.getItems().stream()
                .map(RecommendationDtos.SubmitItem::getExternalId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .toList();

        Map<String, Place> placeByExt = placeRepository.findByExternalIdIn(extIds).stream()
                .collect(Collectors.toMap(Place::getExternalId, p -> p, (a, b) -> a));

        // 4) 결과 버킷
        RecommendationDtos.SubmitResponse out = new RecommendationDtos.SubmitResponse();
        int saved = 0, conflicts = 0, outOfRadius = 0, notFound = 0, invalid = 0;

        for (RecommendationDtos.SubmitItem it : req.getItems()) {
            String externalId = safeTrim(it.getExternalId());
            Map<String, String> invalidDetails = new LinkedHashMap<>();

            // 4-1) 필드 유효성
            if (externalId == null || !(externalId.startsWith("kakao:") || externalId.startsWith("mock:"))) {
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
            if (img != null && img.length() > 1000) {
                invalidDetails.put("image_url", "max_length_1000");
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

            // 4-2) 장소 존재성
            Place place = placeByExt.get(externalId);
            if (place == null) {
                out.getNotFound().add(new RecommendationDtos.SimpleItem(externalId));
                notFound++;
                continue;
            }

            // 4-3) 반경 검증(역 lat/lng vs place y/x)
            Integer distM = safeDistanceM(lat, lng, place.getY(), place.getX());
            if (distM == null || distM > stationRadiusM) {
                out.getOutOfRadius().add(new RecommendationDtos.OutOfRadiusItem(externalId, distM));
                outOfRadius++;
                continue;
            }

            // 4-4) 집계 로드/생성
            RequestPlaceAggregate agg = aggregateRepository
                    .findByRequestIdAndPlaceExternalId(slug, externalId)
                    .orElseGet(() -> new RequestPlaceAggregate(slug, externalId));

            // 4-5) 동일 guest 중복 방지
            if (agg.getId() != null && noteRepository.existsByRpaIdAndGuestId(agg.getId(), guestUuid)) {
                out.getConflicts().add(new RecommendationDtos.SimpleItem(externalId));
                conflicts++;
                continue;
            }

            // 4-6) 집계 증가 + 저장
            agg.increaseCount();
            agg = aggregateRepository.save(agg);

            // 4-7) 노트 저장 (JSONB에 List<String> 직접 바인딩)
            RecommendationNote note = new RecommendationNote(
                    agg.getId(),               // rpaId
                    nickname,                  // nickname
                    msg,                       // recommendMessage
                    img,                       // imageUrl
                    tags,                      // tags(JSONB)
                    guestUuid,                 // guestId
                    OffsetDateTime.now()       // createdAt
            );
            noteRepository.save(note);

            // 4-8) 응답(saved) 작성
            out.getSaved().add(new RecommendationDtos.SavedItem(externalId, agg.getRecommendedCount()));
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

    // 문자열/숫자(BigDecimal 포함) 모두 허용하는 안전 파서
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
            Double y = parseCoord(placeY); // Kakao: y = lat
            Double x = parseCoord(placeX); // Kakao: x = lng
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
        // 🔧 BUGFIX: atan2의 인자 순서 (√a, √(1−a)) 가 맞습니다.
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(R * c);
    }
}
*/
