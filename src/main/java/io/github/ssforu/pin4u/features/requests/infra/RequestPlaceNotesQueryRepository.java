package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.places.domain.Place;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * #8 장소 메모 보기 (요청 slug + external_id 컨텍스트)
 * - 읽기 전용 네이티브 쿼리
 * - @Repository 제네릭은 JPA 관리 엔티티(Place)로 고정
 */
public interface RequestPlaceNotesQueryRepository extends Repository<Place, Long> {

    /** 장소 메타(제목/링크) */
    interface PlaceMeta {
        String getExternal_id();
        String getPlace_name();
        String getPlace_url();
    }

    /** 노트 행 */
    interface NoteRow {
        String getNickname();
        String getRecommend_message();
        String getImage_url();
        String getTags_json();     // rn.tags(jsonb) → TEXT
        Instant getCreated_at();   // ★ Instant로 통일
    }

    @Query(value = """
        SELECT
            p.external_id AS external_id,
            p.place_name  AS place_name,
            p.place_url   AS place_url
        FROM requests r
        JOIN request_place_aggregates rpa
          ON rpa.request_id = r.slug
        JOIN places p
          ON p.external_id = rpa.place_external_id
        WHERE r.slug = :slug
          AND p.external_id = :externalId
        LIMIT 1
        """, nativeQuery = true)
    Optional<PlaceMeta> findPlaceMeta(
            @Param("slug") String slug,
            @Param("externalId") String externalId
    );

    @Query(value = """
        SELECT
            rn.nickname                  AS nickname,
            rn.recommend_message         AS recommend_message,
            rn.image_url                 AS image_url,
            CAST(rn.tags AS TEXT)        AS tags_json,
            rn.created_at                AS created_at
        FROM requests r
        JOIN request_place_aggregates rpa
          ON rpa.request_id = r.slug
        JOIN places p
          ON p.external_id = rpa.place_external_id
        JOIN recommendation_notes rn
          ON rn.rpa_id = rpa.id
        WHERE r.slug = :slug
          AND p.external_id = :externalId
        ORDER BY rn.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<NoteRow> findNotes(
            @Param("slug") String slug,
            @Param("externalId") String externalId,
            @Param("limit") int limit
    );
}
