package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.common.util.SlugGenerator;
import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RequestServiceImpl implements RequestService {
    private final RequestRepository requestRepo;
    private final StationRepository stationRepo;
    private final SlugGenerator slugGenerator; // util

    @Override
    public RequestDtos.CreateResp create(RequestDtos.CreateReq req) {
        // station_code 검증 → slug 생성(Base62) → 저장 → DTO 변환
        throw new UnsupportedOperationException("임시 TODO: implement create(req)");
    }

    @Override @Transactional(readOnly = true)
    public RequestDtos.ListResp list() {
        throw new UnsupportedOperationException("임시 TODO: implement list()");
    }

    @Override @Transactional(readOnly = true)
    public RequestDtos.DetailResp detail(String slug) {
        // 핀+카드 데이터 조합
        throw new UnsupportedOperationException("임시 TODO: implement detail(slug)");
    }
}
