package io.github.ssforu.pin4u.features.requests.api;

import io.github.ssforu.pin4u.common.response.ApiResponse;
import io.github.ssforu.pin4u.features.requests.application.RequestService;
import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RequestController {
    private final RequestService requestService;

    @PostMapping("/requests")
    public ApiResponse<RequestDtos.CreateResp> create(@Valid @RequestBody RequestDtos.CreateReq req) {
        return ApiResponse.success(requestService.create(req));
    }

    @GetMapping("/requests")
    public ApiResponse<RequestDtos.ListResp> list() {
        return ApiResponse.success(requestService.list());
    }

    @GetMapping("/requests/{slug}")
    public ApiResponse<RequestDtos.DetailResp> detail(@PathVariable String slug) {
        return ApiResponse.success(requestService.detail(slug));
    }
}
