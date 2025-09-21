package io.github.ssforu.pin4u.features.requests.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.requests.application.RequestDetailService;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos.RequestDetailResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

// ✅ Swagger 문서용 어노테이션 import
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Requests")
@RestController
@RequestMapping("/api/requests")
public class RequestDetailController {

    private final RequestDetailService requestDetailService;

    public RequestDetailController(RequestDetailService requestDetailService) {
        this.requestDetailService = requestDetailService;
    }

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
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "include_ai", defaultValue = "false") boolean includeAi
    ) {
        RequestDetailResponse data = requestDetailService.getRequestDetail(slug, limit, includeAi);
        return ApiResponse.success(data);
    }
}
