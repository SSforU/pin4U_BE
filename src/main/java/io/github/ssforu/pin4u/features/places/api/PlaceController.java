package io.github.ssforu.pin4u.features.places.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.places.application.PlaceSearchService;
import io.github.ssforu.pin4u.features.places.dto.PlaceDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*; // @GetMapping 등


@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {
    private final PlaceSearchService service;

    @GetMapping("/search")
    public ApiResponse<PlaceDtos.SearchResp> search(
            @RequestParam("q") String q,
            @RequestParam(value="station", required=false) String station,
            @RequestParam(value="limit", defaultValue="10") int limit
    ) {
        return ApiResponse.success(service.search(q, station, limit));
    }
}
