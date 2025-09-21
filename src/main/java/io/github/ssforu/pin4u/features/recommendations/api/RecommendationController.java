package io.github.ssforu.pin4u.features.recommendations.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.recommendations.application.RecommendationService;
import io.github.ssforu.pin4u.features.recommendations.dto.RecommendationDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

// ✅ Swagger 문서용 어노테이션 import
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Recommendations")
@RestController
@RequestMapping("/api/requests/{slug}/recommendations")
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);
    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @Operation(summary = "추천 장소 제출", description = "요청 슬러그에 대한 추천 장소들을 제출합니다.")
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

        } catch (DataIntegrityViolationException e) {
            String msg = (e.getMostSpecificCause() != null) ? e.getMostSpecificCause().getMessage() : e.getMessage();
            log.error("DB upsert failed: {}", msg, e);
            return ResponseEntity.internalServerError().body(
                    ApiResponse.error("INTERNAL_ERROR", "db upsert failed: " + msg, null)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("VALIDATION_ERROR", e.getMessage(), null)
            );

        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ResponseEntity.internalServerError().body(
                    ApiResponse.error("INTERNAL_ERROR", "db upsert failed", null)
            );
        }
    }
}
