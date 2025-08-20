// src/main/java/io/github/ssforu/pin4u/features/requests/api/RequestController.java
package io.github.ssforu.pin4u.features.requests.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.requests.application.RequestService;
import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestService requestService;
    public RequestController(RequestService requestService) { this.requestService = requestService; }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody RequestDtos.CreateRequest req) {
        var created = requestService.create(req.ownerNickname(), req.stationCode(), req.requestMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("request", created);

        return ResponseEntity.created(URI.create("/r/" + created.slug()))
                .body(ApiResponse.success(body));
    }

    @GetMapping
    public ApiResponse<RequestDtos.ListResponse> list() {
        List<RequestDtos.ListItem> items = requestService.list();
        return ApiResponse.success(new RequestDtos.ListResponse(items, items.size()));
    }
}
