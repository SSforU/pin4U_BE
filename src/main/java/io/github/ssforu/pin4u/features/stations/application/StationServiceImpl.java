// src/main/java/io/github/ssforu/pin4u/features/stations/application/StationServiceImpl.java
package io.github.ssforu.pin4u.features.stations.application;

import io.github.ssforu.pin4u.features.stations.dto.StationDtos;
import io.github.ssforu.pin4u.features.stations.dto.StationDtos.SearchResponse;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class StationServiceImpl implements StationService {

    private final StationRepository stationRepository;

    public StationServiceImpl(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @Override
    public SearchResponse search(String q, int limit) {
        if (q == null || q.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q is required");
        if (limit < 1 || limit > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 50");

        // ✅ 메서드명만 교체 (기능/논리 불변)
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
