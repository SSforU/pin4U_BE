package io.github.ssforu.pin4u.features.requests.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ssforu.pin4u.features.requests.dto.RequestPlaceNotesDtos;
import io.github.ssforu.pin4u.features.requests.infra.RequestPlaceNotesQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RequestPlaceNotesServiceImpl implements RequestPlaceNotesService {

    private final RequestPlaceNotesQueryRepository repo;
    private final ObjectMapper objectMapper;

    @Override
    public RequestPlaceNotesDtos.Response getNotes(String slug, String externalId, Integer limit) {
        int lim = (limit == null || limit <= 0 || limit > 100) ? 50 : limit;

        var meta = repo.findPlaceMeta(slug, externalId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND, "place not found for request"));

        var rows = repo.findNotes(slug, externalId, lim);

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
