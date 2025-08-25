package io.github.ssforu.pin4u.features.requests.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.requests.application.RequestPlaceNotesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestPlaceNotesController {

    private final RequestPlaceNotesService service;

    @GetMapping("/{slug}/places/notes")
    public ApiResponse getNotes(
            @PathVariable String slug,
            @RequestParam("external_id") String externalId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ApiResponse.success(service.getNotes(slug, externalId, limit));
    }
}
