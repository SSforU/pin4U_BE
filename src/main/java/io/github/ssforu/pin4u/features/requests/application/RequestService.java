// src/main/java/io/github/ssforu/pin4u/features/requests/application/RequestService.java
package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;
import java.util.List;

public interface RequestService {
    RequestDtos.CreatedRequestDTO create(String ownerNickname, String stationCode, String requestMessage);
    List<RequestDtos.ListItem> list();
    RequestDtos.ListItem get(String slug);

}
