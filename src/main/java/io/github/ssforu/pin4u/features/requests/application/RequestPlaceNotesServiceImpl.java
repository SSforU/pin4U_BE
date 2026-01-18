package io.github.ssforu.pin4u.features.requests.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.requests.domain.RequestPlaceAggregate;
import io.github.ssforu.pin4u.features.requests.dto.RequestPlaceNotesDtos;
import io.github.ssforu.pin4u.features.requests.event.RequestCreatedEvent;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceAggregateRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceNotesQueryRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.requests.infra.SlugGenerator;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestPlaceNotesServiceImpl implements RequestPlaceNotesService {

    // 조회용 의존성
    private final RequestPlaceNotesQueryRepository queryRepo;
    private final ObjectMapper objectMapper;

    // 생성용 의존성
    private final RequestRepository requestRepository;
    private final StationRepository stationRepository;
    private final RequestPlaceAggregateRepository rpaRepository;
    private final SlugGenerator slugGenerator;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * [Read] 노트 조회 (기존 로직 유지)
     */
    @Override
    @Transactional(readOnly = true)
    public RequestPlaceNotesDtos.Response getNotes(String slug, String externalId, Integer limit) {
        int lim = (limit == null || limit <= 0 || limit > 100) ? 50 : limit;

        var meta = queryRepo.findPlaceMeta(slug, externalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "place not found for request"));

        var rows = queryRepo.findNotes(slug, externalId, lim);

        List<RequestPlaceNotesDtos.Note> notes = rows.stream().map(r -> {
            List<String> tags = parseTagsSafe(r.getTags_json());
            return new RequestPlaceNotesDtos.Note(
                    r.getNickname(),
                    r.getRecommend_message(),
                    r.getImage_url(),
                    tags,
                    r.getCreated_at()
            );
        }).toList();

        return new RequestPlaceNotesDtos.Response(
                meta.getExternal_id(),
                meta.getPlace_name(),
                meta.getPlace_url(),
                notes
        );
    }

    /**
     * [Write] 요청 생성 및 비동기 이벤트 발행 (Theme 2 핵심 로직)
     */
    @Override
    @Transactional
    public String createRequestWithNotes(Long userId, RequestPlaceNotesDtos.CreateRequest req) {
        // 1. 역 검증
        if (!stationRepository.existsByCode(req.stationCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_station");
        }

        // 2. Request 생성
        String slug = slugGenerator.generate(req.stationCode());
        Request savedRequest = requestRepository.save(new Request(
                slug,
                userId,
                req.stationCode(),
                req.groupId(),
                req.message() == null ? "" : req.message()
        ));

        // 3. RPA(집계) 생성
        List<RequestPlaceAggregate> aggregates = req.places().stream()
                .map(p -> new RequestPlaceAggregate(savedRequest.getSlug(), p.placeId()))
                .toList();
        rpaRepository.saveAll(aggregates);

        // 4. [Async] AI 요약 요청 -> 이벤트 발행
        eventPublisher.publishEvent(new RequestCreatedEvent(savedRequest.getSlug(), userId));

        return savedRequest.getSlug();
    }

    // Helper Method
    private List<String> parseTagsSafe(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("[notes] tags JSON parse failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}