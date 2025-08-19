package io.github.ssforu.pin4u.features.stations.application;

import io.github.ssforu.pin4u.features.stations.dto.StationDtos.SearchResponse;

public interface StationService {
    SearchResponse search(String q, int limit);
}
