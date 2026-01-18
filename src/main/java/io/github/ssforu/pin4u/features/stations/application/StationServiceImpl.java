package io.github.ssforu.pin4u.features.stations.application;

import io.github.ssforu.pin4u.features.stations.dto.StationDtos;
import io.github.ssforu.pin4u.features.stations.dto.StationDtos.SearchResponse;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class StationServiceImpl implements StationService {

    private final StationRepository stationRepository;

    public StationServiceImpl(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @Override
    @Transactional(readOnly = true) // [Theme 1] 읽기 전용 트랜잭션으로 DB 부하 감소
    @Cacheable(value = "stations", key = "#q + ':' + #limit", unless = "#result.count == 0") // [Theme 1] 검색 결과 캐싱 (빈 결과는 제외)
    public SearchResponse search(String q, int limit) {
        if (q == null || q.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q is required");
        if (limit < 1 || limit > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 50");

        // DB 조회 (인덱스가 있다면 빠르지만, 캐시가 있으면 아예 실행되지 않음)
        var page = stationRepository.findByNameContainingIgnoreCase(q.trim(), PageRequest.of(0, limit));

        List<StationDtos.StationItem> items = page.getContent().stream().map(s ->
                new StationDtos.StationItem(
                        s.getCode(), s.getName(), s.getLine(),
                        s.getLat(), s.getLng()
                )
        ).toList();

        return new SearchResponse(items, items.size());
    }
}