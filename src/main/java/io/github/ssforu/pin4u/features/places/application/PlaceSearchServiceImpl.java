// src/main/java/io/github/ssforu/pin4u/features/places/application/PlaceSearchServiceImpl.java
package io.github.ssforu.pin4u.features.places.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ssforu.pin4u.common.exception.ApiErrorCode;
import io.github.ssforu.pin4u.common.exception.ApiException;
import io.github.ssforu.pin4u.features.places.domain.KakaoPayload;
import io.github.ssforu.pin4u.features.places.domain.KakaoSearchPort;
import io.github.ssforu.pin4u.features.places.dto.PlaceDtos;
import io.github.ssforu.pin4u.features.places.infra.PlaceMockRepository;
import io.github.ssforu.pin4u.features.places.infra.PlaceRepositoryAdapterImpl;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlaceSearchServiceImpl implements PlaceSearchService {

    private final StationRepository stationRepository;
    private final KakaoSearchPort kakaoSearchPort;
    private final PlaceRepositoryAdapterImpl placeUpsertAdapter;
    private final PlaceMockRepository mockRepository;
    private final ObjectMapper om;
    private final int radiusM;
    private final int topN;

    public PlaceSearchServiceImpl(
            StationRepository stationRepository,
            KakaoSearchPort kakaoSearchPort,
            PlaceRepositoryAdapterImpl placeUpsertAdapter,
            PlaceMockRepository mockRepository,
            ObjectMapper om,
            @Value("${app.search.stationRadiusM:800}") int radiusM,
            @Value("${app.search.topN:10}") int topN
    ) {
        this.stationRepository = stationRepository;
        this.kakaoSearchPort = kakaoSearchPort;
        this.placeUpsertAdapter = placeUpsertAdapter;
        this.mockRepository = mockRepository;
        this.om = om;
        this.radiusM = radiusM;
        this.topN = topN;
    }

    @Override
    @Transactional // write 허용(places upsert 포함)
    public PlaceDtos.SearchResponse search(String stationCode, String q, Integer limit) {
        // 0) limit 정규화 (1~50만 허용, 아니면 topN 사용)
        final int size = (limit != null && limit >= 1 && limit <= 50) ? limit : topN;

        // 1) 입력 정리/검증
        final String station = (stationCode == null) ? null : stationCode.trim();
        final String keyword = (q == null) ? null : q.trim();

        if (!StringUtils.hasText(station) || !StringUtils.hasText(keyword)) {
            Map<String, Object> details = new LinkedHashMap<>();
            if (!StringUtils.hasText(keyword)) details.put("q", "must not be blank");
            if (!StringUtils.hasText(station)) details.put("station", "required");
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid request", details);
        }

        // 2) 역 조회
        var st = stationRepository.findByCode(station)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.NOT_FOUND, "station_code not found", Map.of("station", station)));

        // 3) 좌표(BigDecimal 유지)
        BigDecimal lat = st.getLat();
        BigDecimal lng = st.getLng();

        // 4) 카카오 검색 (반경/TopN→size 정책 적용)
        List<KakaoPayload.Document> docs =
                kakaoSearchPort.keywordSearch(lat, lng, keyword, radiusM, size);

        // 5) places upsert
        placeUpsertAdapter.upsertFromKakao(docs);

        // 6) place_mock 병합 준비
        List<String> externalIds = docs.stream().map(d -> "kakao:" + d.id()).toList();
        Map<String, io.github.ssforu.pin4u.features.places.domain.PlaceMock> mocks =
                mockRepository.findByExternalIdIn(externalIds).stream()
                        .collect(Collectors.toMap(
                                io.github.ssforu.pin4u.features.places.domain.PlaceMock::getExternalId,
                                Function.identity()
                        ));

        // 7) DTO 조립 + 정렬 (거리↑ → 평점↓ → 평점수↓), 그리고 size 제한
        List<PlaceDtos.Item> items = docs.stream().map(d -> {
            String externalId = "kakao:" + d.id();
            Integer distanceM = safeDistance(d, lat, lng);

            var mock = mocks.get(externalId);
            PlaceDtos.MockDto mockDto = null;
            if (mock != null) {
                mockDto = new PlaceDtos.MockDto(
                        mock.getRating() == null ? null : mock.getRating().doubleValue(),
                        mock.getRatingCount(),
                        parseList(mock.getReviewSnippetsJson()),
                        parseList(mock.getImageUrlsJson()),
                        parseList(mock.getOpeningHoursJson())
                );
            }

            return new PlaceDtos.Item(
                    externalId,
                    d.id(),
                    d.place_name(),
                    d.category_group_code(),
                    d.category_group_name(),
                    d.category_name(),
                    d.phone(),
                    d.address_name(),
                    d.road_address_name(),
                    d.x(),
                    d.y(),
                    d.place_url(),
                    distanceM,
                    mockDto
            );
        }).sorted(
                Comparator
                        .comparing(PlaceDtos.Item::distance_m, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(
                                (PlaceDtos.Item it) -> Optional.ofNullable(it.mock())
                                        .map(PlaceDtos.MockDto::rating).orElse(0.0),
                                Comparator.reverseOrder()
                        )
                        .thenComparing(
                                (PlaceDtos.Item it) -> Optional.ofNullable(it.mock())
                                        .map(PlaceDtos.MockDto::rating_count).orElse(0),
                                Comparator.reverseOrder()
                        )
        ).limit(size).toList();

        // 8) 역 요약
        var stationBrief = new PlaceDtos.StationBrief(
                st.getCode(), st.getName(), st.getLine(), lat, lng
        );

        // 9) 응답
        return new PlaceDtos.SearchResponse(stationBrief, items);
    }

    /** Kakao distance(m) 우선, 없으면 하버사인 */
    private Integer safeDistance(KakaoPayload.Document d, BigDecimal lat, BigDecimal lng) {
        try {
            String dist = d.distance();
            if (dist != null && !dist.isBlank()) return Integer.parseInt(dist);
        } catch (Exception ignore) {}
        try {
            double y = Double.parseDouble(d.y());
            double x = Double.parseDouble(d.x());
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
        double a = Math.sin(dφ/2)*Math.sin(dφ/2) +
                Math.cos(φ1)*Math.cos(φ2)*Math.sin(dλ/2)*Math.sin(dλ/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return (int)Math.round(R * c);
    }

    /** JSON 문자열 → List<String> */
    private List<String> parseList(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) return null;
        try {
            return om.readValue(jsonText, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
