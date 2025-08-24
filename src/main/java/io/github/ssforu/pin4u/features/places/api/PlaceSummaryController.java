
package io.github.ssforu.pin4u.features.places.api;

import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping("/api/places")
public class PlaceSummaryController {

    public record SummaryResponse(String external_id, String summary, String source) {}

    @GetMapping("/summary")
    public SummaryResponse getSummary(@RequestParam("external_id") String externalId) {
        // 다음 단계에서 실제 DB/서비스 로직 연결
        return new SummaryResponse(externalId, "PLACE_SUMMARY_NOT_READY", "stub");
    }
}
