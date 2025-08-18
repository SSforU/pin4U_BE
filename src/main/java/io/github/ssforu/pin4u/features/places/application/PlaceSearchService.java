package io.github.ssforu.pin4u.features.places.application;

import io.github.ssforu.pin4u.features.places.dto.PlaceDtos;

public interface PlaceSearchService {
    PlaceDtos.SearchResp search(String q, String stationCode, int limit);
}
