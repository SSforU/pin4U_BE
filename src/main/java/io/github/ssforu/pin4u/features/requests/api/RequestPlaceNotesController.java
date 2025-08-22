package io.github.ssforu.pin4u.features.requests.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.requests.application.RequestPlaceNotesService;
import io.github.ssforu.pin4u.features.requests.dto.RequestPlaceNotesDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestPlaceNotesController {

    private final RequestPlaceNotesService service;

    /**
     * GET /api/requests/{slug}/places/notes?external_id=...&limit=...
     */
    @GetMapping("/{slug}/places/notes")
    public ApiResponse<RequestPlaceNotesDtos.Response> getNotes(
            @PathVariable String slug,
            @RequestParam("external_id") String externalId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        var data = service.getNotes(slug, externalId, limit);
        return ApiResponse.success(data);
    }
}
