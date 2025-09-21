package io.github.ssforu.pin4u.features.recommendations.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.recommendations.application.AutoRecommendationService;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos.RequestDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

// ✅ Swagger 문서용 어노테이션 import
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Recommendations")
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class AutoRecommendationController {

    private final AutoRecommendationService service;

    /**
     * #10 자동 장소추천
     * GET /api/recommendations/auto?slug=&n=&q=
     * - n: 기본 1, 최대 5
     * - q: 있으면 그대로, 없으면 request_message에서 AI로 키워드(최대 2) 추출
     * - 응답: #7과 동일 스키마(RequestDetailResponse)
     */
    @Operation(summary = "자동 장소추천", description = "요청 슬러그 기준으로 자동 추천을 수행합니다.")
    @GetMapping("/auto")
    public ApiResponse<RequestDetailResponse> auto(
            @RequestParam("slug") String slug,
            @RequestParam(value = "n", required = false) Integer n,
            @RequestParam(value = "q", required = false) String q
    ) {
        return ApiResponse.success(service.recommend(slug, n, q));
    }
}
