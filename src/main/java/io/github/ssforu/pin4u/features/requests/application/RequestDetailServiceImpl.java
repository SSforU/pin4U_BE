package io.github.ssforu.pin4u.features.requests.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos;
import io.github.ssforu.pin4u.features.requests.dto.RequestDetailDtos.*;
import io.github.ssforu.pin4u.features.requests.infra.RequestDetailQueryRepository;
import io.github.ssforu.pin4u.features.requests.infra.RequestDetailQueryRepository.Row;
import io.github.ssforu.pin4u.features.requests.infra.RequestRepository;
import io.github.ssforu.pin4u.features.stations.infra.StationRepository;
import io.github.ssforu.pin4u.features.requests.domain.Request;
import io.github.ssforu.pin4u.features.stations.domain.Station;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class RequestDetailServiceImpl implements RequestDetailService {

    private final RequestRepository requestRepository;
    private final StationRepository stationRepository;
    private final RequestDetailQueryRepository queryRepository;
    private final ObjectMapper objectMapper;

    public RequestDetailServiceImpl(
            RequestRepository requestRepository,
            StationRepository stationRepository,
            RequestDetailQueryRepository queryRepository,
            ObjectMapper objectMapper
    ) {
        this.requestRepository = requestRepository;
        this.stationRepository = stationRepository;
        this.queryRepository = queryRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public RequestDetailResponse getRequestDetail(String slug, Integer limit, boolean includeAi) {
        // 1) 요청/역 조회
        Request req = requestRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "request not found"));
        Station st = stationRepository.findByCode(req.getStationCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "station not found"));

        // 2) limit 클램프(기본 12, 1~50)
        int lim = (limit == null) ? 12 : Math.min(Math.max(limit, 1), 50);

        // 3) 목록 조회
        List<Row> rows = queryRepository.findItemsBySlug(slug, lim);

        // 4) 매핑
        List<Item> items = new ArrayList<>(rows.size());
        for (Row r : rows) {
            Mock mock = null;
            if (r.getMock_rating() != null || r.getMock_rating_count() != null
                    || r.getMock_image_urls_json() != null || r.getMock_opening_hours_json() != null) {
                List<String> imageUrls = parseJsonArrayOfString(r.getMock_image_urls_json());
                List<String> openingHours = parseJsonArrayOfString(r.getMock_opening_hours_json());
                mock = new Mock(r.getMock_rating(), r.getMock_rating_count(), imageUrls, openingHours);
            }

            Ai ai = null;
            if (includeAi && (r.getAi_summary_text() != null || r.getAi_evidence_json() != null)) {
                Object evidence = parseJsonGeneric(r.getAi_evidence_json());
                OffsetDateTime updated = r.getAi_updated_at();
                ai = new Ai(r.getAi_summary_text(), evidence, updated);
            }

            Item item = new Item(
                    r.getExternal_id(),
                    r.getId_stripped(),
                    r.getPlace_name(),
                    r.getCategory_group_code(),
                    r.getCategory_group_name(),
                    r.getCategory_name(),
                    r.getAddress_name(),
                    r.getRoad_address_name(),
                    r.getX(),
                    r.getY(),
                    r.getDistance_m(),
                    r.getPlace_url(),
                    mock,
                    ai,
                    r.getRecommended_count()
            );
            items.add(item);
        }

        RequestDetailDtos.Station dtoStation = new RequestDetailDtos.Station(
                st.getCode(),
                st.getName(),
                st.getLine(),
                toBigDecimal(st.getLat()),
                toBigDecimal(st.getLng())
        );

        return new RequestDetailResponse(
                req.getSlug(),
                dtoStation,
                req.getRequestMessage(),
                items
        );
    }

    private List<String> parseJsonArrayOfString(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null; // 파싱 실패 시 null 허용
        }
    }

    private Object parseJsonGeneric(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }
}
