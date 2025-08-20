// src/main/java/io/github/ssforu/pin4u/features/requests/application/RequestServiceImpl.java
package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.requests.infra.SlugGenerator;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final StationRepository stationRepository;
    private final SlugGenerator slugGenerator;

    public RequestServiceImpl(
            RequestRepository requestRepository,
            StationRepository stationRepository,
            SlugGenerator slugGenerator
    ) {
        this.requestRepository = requestRepository;
        this.stationRepository = stationRepository;
        this.slugGenerator = slugGenerator;
    }

    @Override
    public RequestDtos.CreatedRequestDTO create(String ownerNickname, String stationCode, String requestMessage) {
        // 1) 역 코드 유효성 검사 (없으면 400/IllegalArgumentException 던짐)
        stationRepository.findByCode(stationCode)
                .orElseThrow(() -> new IllegalArgumentException("invalid station_code: " + stationCode));

        // 2) slug 생성 (규칙: seed=stationCode, yyyyMMddHHmmss + 8자리 UUID suffix)
        String slug = slugGenerator.generate(stationCode);

        // 3) 엔티티 생성 (엔티티 생성자 사용 — setter 없음)
        Request entity = new Request(slug, ownerNickname, stationCode, requestMessage);

        // 4) 저장
        Request saved = requestRepository.save(entity);

        // 5) DTO 변환 (네가 준 DTO 구조 그대로)
        return new RequestDtos.CreatedRequestDTO(
                saved.getSlug(),
                saved.getOwnerNickname(),
                saved.getStationCode(),
                saved.getRequestMessage(),
                saved.getCreatedAt()
        );
    }

    @Override
    public List<RequestDtos.ListItem> list() {
        return requestRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(r -> new RequestDtos.ListItem(
                        r.getSlug(),
                        r.getOwnerNickname(),
                        r.getStationCode(),
                        r.getRequestMessage(),
                        r.getRecommendCount(),
                        r.getCreatedAt()
                ))
                .toList();
    }
}
