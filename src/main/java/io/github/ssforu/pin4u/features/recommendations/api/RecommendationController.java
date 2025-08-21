// src/main/java/io/github/ssforu/pin4u/features/recommendations/api/RecommendationController.java
package io.github.ssforu.pin4u.features.recommendations.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.recommendations.application.RecommendationService;
import io.github.ssforu.pin4u.features.recommendations.dto.RecommendationDtos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/requests/{slug}/recommendations")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RecommendationDtos.SubmitResponse>> submit(
            @PathVariable("slug") String slug,
            @RequestBody RecommendationDtos.SubmitRequest body
    ) {
        try {
            if (body == null || body.getItems() == null || body.getItems().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("VALIDATION_ERROR", "items must not be empty",
                                java.util.Map.of("items", "required"))
                );
            }

            var resp = service.submit(slug, body);
            return ResponseEntity.ok(ApiResponse.success(resp));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", e.getMessage(), java.util.Map.of("slug", slug))
            );
        } catch (IllegalArgumentException e) { // UUID 등 포맷 오류
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("VALIDATION_ERROR", e.getMessage(), null)
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    ApiResponse.error("INTERNAL_ERROR", "db upsert failed", null)
            );
        }
    }
}
