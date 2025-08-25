// src/main/java/io/github/ssforu/pin4u/features/places/api/PlaceController.java
package io.github.ssforu.pin4u.features.places.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.places.application.PlaceSearchService;
import io.github.ssforu.pin4u.features.places.dto.PlaceDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService service;

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PlaceDtos.SearchResponse> search(
            @RequestParam("station") String station,
            @RequestParam("q") String q,
            @RequestParam(value = "limit", required = false) Integer limit // ✅ 추가
    ) {
        var data = service.search(station, q, limit); // ✅ 시그니처 확장
        return ApiResponse.success(data);
    }
}
