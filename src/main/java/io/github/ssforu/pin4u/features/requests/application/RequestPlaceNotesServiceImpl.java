package io.github.ssforu.pin4u.features.requests.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ssforu.pin4u.features.requests.dto.RequestPlaceNotesDtos;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceNotesQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestPlaceNotesServiceImpl implements RequestPlaceNotesService {

    private final RequestPlaceNotesQueryRepository repo;
    private final ObjectMapper om;

    @Override
    public RequestPlaceNotesDtos.Response getNotes(String slug, String externalId, Integer limit) {
        int lim = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);

        var placeMeta = repo.findPlaceMeta(slug, externalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "place not found for request"));

        var rows = repo.findNotes(slug, externalId, lim);

        var notes = rows.stream().map(r -> new RequestPlaceNotesDtos.Note(
                r.getNickname(),
                r.getRecommend_message(),
                r.getImage_url(),
                parseList(r.getTags_json()),
                r.getCreated_at()
        )).toList();

        return new RequestPlaceNotesDtos.Response(
                placeMeta.getExternal_id(),
                placeMeta.getPlace_name(),
                placeMeta.getPlace_url(),
                notes
        );
    }

    private List<String> parseList(String json) {
        try {
            if (json == null) return null;
            return om.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            // JSON이 손상된 경우에도 API는 깨지지 않게 빈 배열로 처리
            return List.of();
        }
    }
}
