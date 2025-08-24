package io.github.ssforu.pin4u.features.places.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.places.application.PlaceSearchService;
import io.github.ssforu.pin4u.features.places.dto.PlaceDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceSearchService service;

    @GetMapping("/search")
    public ApiResponse<PlaceDtos.SearchResponse> search(
            @RequestParam("station") String station,
            @RequestParam("q") String q
    ) {
        var data = service.search(station, q);
        return ApiResponse.success(data);
    }
}
