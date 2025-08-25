package io.github.ssforu.pin4u.features.recommendations.application;

import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos.RequestDetailResponse;

public interface AutoRecommendationService {
    RequestDetailResponse recommend(String slug, Integer n, String q);
}
