// src/main/java/io/github/ssforu/pin4u/features/requests/application/RequestServiceImpl.java
package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.requests.dto.RequestDtos;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.requests.infra.SlugGenerator;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;
    private final StationRepository stationRepository;
    private final SlugGenerator slugGenerator;

    public RequestServiceImpl(RequestRepository requestRepository,
                              StationRepository stationRepository,
                              SlugGenerator slugGenerator) {
        this.requestRepository = requestRepository;
        this.stationRepository = stationRepository;
        this.slugGenerator = slugGenerator;
    }

    @Override
    @Transactional
    public RequestDtos.CreatedRequestDTO create(String ownerNickname, String stationCode, String requestMessage) {
        // (옵션) 역 코드 검증을 하려면 주석 해제
        // stationRepository.findByCode(stationCode)
        //      .orElseThrow(() -> new IllegalArgumentException("unknown station: " + stationCode));

        String slug = slugGenerator.generate(stationCode);
        Request saved = requestRepository.save(new Request(slug, ownerNickname, stationCode, requestMessage));

        return new RequestDtos.CreatedRequestDTO(
                saved.getSlug(),
                saved.getOwnerNickname(),
                saved.getStationCode(),
                saved.getRequestMessage(),
                saved.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequestDtos.ListItem> list() {
        return requestRepository.findAllByOrderByCreatedAtDesc().stream()
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
