package io.github.ssforu.pin4u.features.requests.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.requests.application.RequestPlaceNotesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

// ✅ Swagger 문서용 어노테이션 import
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Requests")
@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestPlaceNotesController {

    private final RequestPlaceNotesService service;

    @Operation(summary = "장소 메모 조회", description = "요청 슬러그 + 외부 장소 ID로 메모를 조회합니다.")
    @GetMapping("/{slug}/places/notes")
    public ApiResponse getNotes(
            @PathVariable String slug,
            @RequestParam("external_id") String externalId,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ApiResponse.success(service.getNotes(slug, externalId, limit));
    }
}
