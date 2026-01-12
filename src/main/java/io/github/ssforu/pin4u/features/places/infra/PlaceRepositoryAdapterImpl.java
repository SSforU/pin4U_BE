// src/main/java/io/github/ssforu/pin4u/features/places/infra/PlaceRepositoryAdapterImpl.java
package io.github.ssforu.pin4u.features.places.infra;

import io.github.ssforu.pin4u.features.places.domain.KakaoPayload;
import io.github.ssforu.pin4u.features.places.domain.Place;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class PlaceRepositoryAdapterImpl {
    private final PlaceRepository placeRepository;

    public PlaceRepositoryAdapterImpl(PlaceRepository placeRepository) {
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
                            .categoryGroupCode(safe(d.category_group_code()))   // ★ 추가
                            .categoryGroupName(safe(d.category_group_name()))   // ★ 추가
                            .categoryName(safe(d.category_name()))
                            .phone(safe(d.phone()))
                            .addressName(safe(d.address_name()))                // ★ 추가
                            .roadAddressName(safe(d.road_address_name()))
                            .x(d.x()) // 문자열 그대로
                            .y(d.y()) // 문자열 그대로
                            .placeUrl(safe(d.place_url()))
                            .createdAt(now)
                            .updatedAt(now)
                            .build()
            );

            // 항상 최신값으로 갱신
            p.setPlaceName(safe(d.place_name()));
            p.setCategoryGroupCode(safe(d.category_group_code()));   // ★ 추가
            p.setCategoryGroupName(safe(d.category_group_name()));   // ★ 추가
            p.setCategoryName(safe(d.category_name()));
            p.setPhone(safe(d.phone()));
            p.setAddressName(safe(d.address_name()));                // ★ 추가
            p.setRoadAddressName(safe(d.road_address_name()));
            p.setX(d.x());
            p.setY(d.y());
            p.setPlaceUrl(safe(d.place_url()));
            p.setUpdatedAt(now);

            placeRepository.save(p);
        }
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
