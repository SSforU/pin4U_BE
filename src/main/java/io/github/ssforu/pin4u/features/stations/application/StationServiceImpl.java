package io.github.ssforu.pin4u.features.stations.application;

import io.github.ssforu.pin4u.features.stations.dto.StationDtos;
import io.github.ssforu.pin4u.features.stations.dto.StationDtos.SearchResponse;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StationServiceImpl implements StationService {

    private final StationRepository stationRepository;

    public StationServiceImpl(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    // 캐시 키에 limit을 제외하고 최대치(50)로 한 번 조회해 캐시한 뒤 애플리케이션에서 잘라냄.
    // 트레이드오프:
    //   [A] limit을 키에 포함 → 같은 검색어에 limit별 별도 캐시. hit rate 낮음.
    //   [B] limit 제외, 최대치로 캐시 → 메모리 약간 증가하나 hit rate 대폭 향상.
    // 선택: B. 역 검색 결과는 최대 50건이며, 대부분 10건 이내이므로 낭비가 미미하다.
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "stations",
            key = "T(io.github.ssforu.pin4u.features.stations.application.StationServiceImpl).normalizeKey(#q)",
            unless = "#result.count == 0")
    public SearchResponse search(String q, int limit) {
        if (q == null || q.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q is required");
        if (limit < 1 || limit > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 50");

        String normalized = q.trim().toLowerCase();
        var page = stationRepository.findByNameContainingIgnoreCase(normalized, PageRequest.of(0, 50));

        List<StationDtos.StationItem> all = page.getContent().stream().map(s ->
                new StationDtos.StationItem(
                        s.getCode(), s.getName(), s.getLine(),
                        s.getLat(), s.getLng()
                )
        ).toList();

        List<StationDtos.StationItem> items = all.stream().limit(limit).toList();
        return new SearchResponse(items, items.size());
    }

    public static String normalizeKey(String q) {
        if (q == null) return "";
        return q.trim().toLowerCase();
    }
}
