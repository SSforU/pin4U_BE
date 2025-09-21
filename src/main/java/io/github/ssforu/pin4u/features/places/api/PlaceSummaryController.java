package io.github.ssforu.pin4u.features.places.api;

import org.springframework.web.bind.annotation.*;

// ✅ Swagger 문서용 어노테이션 import
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Places")
@RestController
@RequestMapping("/api/places")
public class PlaceSummaryController {

    public record SummaryResponse(String external_id, String summary, String source) {}

    @Operation(summary = "장소 요약(Stub)", description = "다음 단계에서 실제 DB/서비스 로직 연결 예정")
    @GetMapping("/summary")
    public SummaryResponse getSummary(@RequestParam("external_id") String externalId) {
        return new SummaryResponse(externalId, "PLACE_SUMMARY_NOT_READY", "stub");
    }
}
