package io.github.ssforu.pin4u.features.places.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.places.application.PlaceSearchService;
import io.github.ssforu.pin4u.features.places.dto.PlaceDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

// ✅ Swagger 문서용 어노테이션 import
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Places")
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService service;

    @Operation(summary = "장소 검색", description = "역 코드와 쿼리로 반경 내 장소를 검색합니다.")
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PlaceDtos.SearchResponse> search(
            @RequestParam("station") String station,
            @RequestParam("q") String q,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        var data = service.search(station, q, limit);
        return ApiResponse.success(data);
    }
}
