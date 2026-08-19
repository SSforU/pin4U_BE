package io.github.ssforu.pin4u.features.requests.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.requests.application.RequestDetailService;
import io.github.ssforu.pin4u.features.requests.domain.AiSummaryJob;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos.RequestDetailResponse;
import io.github.ssforu.pin4u.features.requests.infra.AiSummaryJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

@Tag(name = "Requests")
@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestDetailController {

    private final RequestDetailService requestDetailService;
    private final AiSummaryJobRepository jobRepository;

    /**
     * #7 A-지도화면(지도의 핀 + 카드뉴스)
     * GET /api/requests/{slug}?limit=&include_ai=
     * - limit 기본 12, 1~50 범위로 서버 클램프
     * - include_ai=true면 캐시가 있을 때만 포함(없으면 ai:null)
     */
    @Operation(summary = "요청 상세", description = "지도의 핀/카드뉴스 등 상세 정보를 반환합니다.")
    @GetMapping("/{slug}")
    public ApiResponse<RequestDetailResponse> get(
            @PathVariable String slug,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        RequestDetailResponse data = requestDetailService.getRequestDetail(slug, limit);
        return ApiResponse.success(data);
    }

    @Operation(summary = "AI 요약 작업 상태", description = "요청에 대한 AI 요약 생성 작업의 현재 상태를 반환합니다.")
    @GetMapping("/{slug}/summary-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summaryStatus(@PathVariable String slug) {
        return jobRepository.findByRequestSlug(slug)
                .map(job -> ResponseEntity.ok(ApiResponse.success(Map.<String, Object>of(
                        "status", job.getStatus().name(),
                        "attempts", job.getAttempts(),
                        "updatedAt", job.getUpdatedAt()
                ))))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success(
                        Map.of("status", "NOT_STARTED")
                )));
    }
}
