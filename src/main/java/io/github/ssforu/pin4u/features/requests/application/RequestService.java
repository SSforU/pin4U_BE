package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;
import java.util.List;

public interface RequestService {

    RequestDtos.CreatedRequestDTO create(Long ownerUserId, String stationCode, String requestMessage, String groupSlug);

    RequestDtos.ListItem get(String slug);

    List<RequestDtos.ListItem> list();

    enum DeleteResult { OK, NOT_OWNER, NOT_FOUND }

    DeleteResult delete(Long me, String slug);

    // ✅ 추가: 요청 슬러그로 오너 정보(닉네임 포함) 조회용
    record OwnerBrief(Long userId, String nickname) {}
    OwnerBrief getOwnerByRequestSlug(String slug);
}
