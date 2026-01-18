package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.features.requests.dto.RequestPlaceNotesDtos;

public interface RequestPlaceNotesService {

    RequestPlaceNotesDtos.Response getNotes(String slug, String externalId, Integer limit);

    // 구현체(Impl)에 추가된 메서드를 인터페이스에도 선언 (정합성 확보)
    String createRequestWithNotes(Long userId, RequestPlaceNotesDtos.CreateRequest req);
}