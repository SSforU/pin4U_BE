package io.github.ssforu.pin4u.features.requests.application;

import io.github.ssforu.pin4u.features.requests.dto.RequestPlaceNotesDtos;

public interface RequestPlaceNotesService {
    RequestPlaceNotesDtos.Response getNotes(String slug, String externalId, Integer limit);
}
