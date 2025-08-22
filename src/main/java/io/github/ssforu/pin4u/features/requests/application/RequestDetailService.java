package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos.RequestDetailResponse;

public interface RequestDetailService {
    RequestDetailResponse getRequestDetail(String slug, Integer limit, boolean includeAi);
}
