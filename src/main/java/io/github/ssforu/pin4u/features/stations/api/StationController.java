package io.github.ssforu.pin4u.features.stations.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.stations.application.StationService;
import io.github.ssforu.pin4u.features.stations.dto.StationDtos.SearchResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) { this.stationService = stationService; }

    @GetMapping("/search")
    public ApiResponse<SearchResponse> search(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return ApiResponse.success(stationService.search(q, limit));
    }
}
