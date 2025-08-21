// src/main/java/io/github/ssforu/pin4u/features/recommendations/application/RecommendationService.java
package io.github.ssforu.pin4u.features.recommendations.application;

import io.github.ssforu.pin4u.features.recommendations.dto.RecommendationDtos;

public interface RecommendationService {
    RecommendationDtos.SubmitResponse submit(String slug, RecommendationDtos.SubmitRequest req);
}
