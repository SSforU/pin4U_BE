package io.github.ssforu.pin4u.features.places.infra;

import io.github.ssforu.pin4u.features.places.domain.KakaoPayload;
import io.github.ssforu.pin4u.features.places.domain.Place;
// import lombok.RequiredArgsConstructor;  // ❌ 지워
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class PlaceRepositoryAdapterImpl {
    private final PlaceRepository placeRepository;

    public PlaceRepositoryAdapterImpl(PlaceRepository placeRepository) { // ✅ 수동 생성자만 사용
        this.placeRepository = placeRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertFromKakao(List<KakaoPayload.Document> docs) {
        OffsetDateTime now = OffsetDateTime.now();
        for (KakaoPayload.Document d : docs) {
            String externalId = "kakao:" + d.id();

            Place p = placeRepository.findByExternalId(externalId).orElseGet(() ->
                    Place.builder()
                            .externalId(externalId)
                            .placeName(safe(d.place_name()))
                            .categoryName(safe(d.category_name()))
                            .phone(safe(d.phone()))
                            .roadAddressName(safe(d.road_address_name()))
                            /*.x(parseBd(d.x()))
                            .y(parseBd(d.y()))*/
                            .x(d.x()) // [FIX] 카카오 파리티: DB/엔티티에는 문자열로 보관 (연산은 사용 시점에 변환)
                            .y(d.y()) // [FIX] 위와 동일
                            .placeUrl(safe(d.place_url()))
                            .createdAt(now)
                            .updatedAt(now)
                            .build()
            );

            p.setPlaceName(safe(d.place_name()));
            p.setCategoryName(safe(d.category_name()));
            p.setPhone(safe(d.phone()));
            p.setRoadAddressName(safe(d.road_address_name()));
           /* p.setX(parseBd(d.x()));
            p.setY(parseBd(d.y()));*/
            p.setX(d.x()); // [FIX] 저장은 문자열(파싱 금지) — 불필요한 형변환로 인한 컴파일/런타임 이슈 방지
            p.setY(d.y()); // [FIX] 저장은 문자열(파싱 금지)
            p.setPlaceUrl(safe(d.place_url()));
            p.setUpdatedAt(now);

            placeRepository.save(p);
        }
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static BigDecimal parseBd(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s); }
        catch (NumberFormatException e) { return null; }
    }
}
