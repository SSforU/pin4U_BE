package io.github.ssforu.pin4u.features.requests.infra;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * #8 장소 메모 보기 (읽기 전용)
 * - 먼저 place 존재/소속 검증
 * - 그 다음 노트 목록 조회
 */
public interface RequestPlaceNotesQueryRepository extends Repository<Object, Long> {

    interface PlaceMetaRow {
        String getExternal_id();
        String getPlace_name();
        String getPlace_url();
    }

    interface NoteRow {
        String getNickname();
        String getRecommend_message();
        String getImage_url();
        String getTags_json();           // jsonb → text로 캐스팅
        OffsetDateTime getCreated_at();
    }

    @Query(value = """
        SELECT
            p.external_id  AS external_id,
            p.place_name   AS place_name,
            p.place_url    AS place_url
        FROM request_place_aggregates rpa
        JOIN places p ON p.external_id = rpa.place_external_id
        WHERE rpa.request_id = :slug
          AND rpa.place_external_id = :externalId
        LIMIT 1
        """, nativeQuery = true)
    Optional<PlaceMetaRow> findPlaceMeta(
            @Param("slug") String slug,
            @Param("externalId") String externalId);

    @Query(value = """
        SELECT
            rn.nickname             AS nickname,
            rn.recommend_message    AS recommend_message,
            rn.image_url            AS image_url,
            CAST(rn.tags AS TEXT)   AS tags_json,
            rn.created_at           AS created_at
        FROM recommendation_notes rn
        JOIN request_place_aggregates rpa ON rpa.id = rn.rpa_id
        WHERE rpa.request_id = :slug
          AND rpa.place_external_id = :externalId
        ORDER BY rn.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<NoteRow> findNotes(
            @Param("slug") String slug,
            @Param("externalId") String externalId,
            @Param("limit") int limit);
}
