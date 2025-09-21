package io.github.ssforu.pin4u.features.stations.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.stations.application.StationService;
import io.github.ssforu.pin4u.features.stations.dto.StationDtos.SearchResponse;
import org.springframework.web.bind.annotation.*;

// ✅ Swagger 문서용 어노테이션 import
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Stations")
@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) { this.stationService = stationService; }

    @Operation(summary = "지하철역 검색", description = "역 이름으로 키워드 검색합니다.")
    @GetMapping("/search")
    public ApiResponse<SearchResponse> search(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return ApiResponse.success(stationService.search(q, limit));
    }
}
