// src/main/java/io/github/ssforu/pin4u/features/places/application/PlaceSearchService.java
package io.github.ssforu.pin4u.features.places.application;

import io.github.ssforu.pin4u.features.places.dto.PlaceDtos;

public interface PlaceSearchService {
    // ✅ limit를 선택 파라미터로 받음
    PlaceDtos.SearchResponse search(String stationCode, String q, Integer limit);

    default PlaceDtos.SearchResponse search(String stationCode, String q) {
        return search(stationCode, q, null);
    }

}
