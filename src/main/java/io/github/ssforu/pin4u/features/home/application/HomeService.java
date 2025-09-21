package io.github.ssforu.pin4u.features.home.application;

import io.github.ssforu.pin4u.features.home.dto.HomeDtos;
import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceAggregateRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.stations.domain.Station;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final RequestRepository requests;
    private final StationRepository stations;
    private final RequestPlaceAggregateRepository rpaRepo;

    public HomeDtos.DashboardResponse dashboard(Long me) {
        List<Request> rows = requests.findAllByOwnerUserIdOrderByCreatedAtDesc(me);

        // station 캐시
        Map<String, Station> stByCode = new HashMap<>();
        List<HomeDtos.Item> items = rows.stream().map(r -> {
            Station st = null;
            if (r.getStationCode() != null && !r.getStationCode().isBlank()) {
                st = stByCode.computeIfAbsent(r.getStationCode(),
                        code -> stations.findByCode(code).orElse(null));
            }
            int total = Optional.ofNullable(rpaRepo.sumByRequestId(r.getSlug())).orElse(0L).intValue();

            return new HomeDtos.Item(
                    r.getSlug(),
                    st != null ? st.getName() : null,  // station_name
                    st != null ? st.getLine() : null,  // station_line
                    null,                               // road_address_name (현재 미사용이면 null 유지)
                    total,                              // recommend_count
                    r.getCreatedAt(),                   // created_at
                    r.getRequestMessage()               // ✅ request_message
            );
        }).toList();

        return new HomeDtos.DashboardResponse(
                items,
                List.of(),                      // groups: 추후 확장
                Map.of("group_owner_pending", 0) // badges: 추후 확장
        );
    }
}
