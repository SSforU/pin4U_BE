package io.github.ssforu.pin4u.features.places.application;

import io.github.ssforu.pin4u.features.places.domain.KakaoSearchPort;
import io.github.ssforu.pin4u.features.places.dto.PlaceDtos;
import io.github.ssforu.pin4u.features.places.infra.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceSearchServiceImpl implements PlaceSearchService {
    private final KakaoSearchPort kakaoPort;         // domain port
    private final PlaceRepository placeRepo;         // upsert 용도

    @Override
    public PlaceDtos.SearchResp search(String q, String stationCode, int limit) {
        // kakaoPort.search(...) → 파리티 DTO 구성 → places upsert → 응답
        throw new UnsupportedOperationException("임시 TODO: implement search(q, stationCode, limit)");
    }
}
