package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;

public interface RequestService {
    RequestDtos.CreateResp create(RequestDtos.CreateReq req);
    RequestDtos.ListResp list();
    RequestDtos.DetailResp detail(String slug);

}
