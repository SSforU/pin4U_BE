package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.places.domain.Place;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RequestPlaceNotesQueryRepository extends Repository<Place, Long> {

    interface PlaceMeta {
        String getExternal_id();
        String getPlace_name();
        String getPlace_url();
    }

    interface NoteRow {
        String getNickname();
        String getRecommend_message();
        String getImage_url();
        String getTags_json();
        Instant getCreated_at();
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
          ON p.id = rpa.place_id
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
            rn.nickname                                  AS nickname,
            rn.recommend_message                         AS recommend_message,
            CASE WHEN rn.image_is_public THEN rn.image_url ELSE NULL END AS image_url,
            CAST(rn.tags AS TEXT)                        AS tags_json,
            rn.created_at                                AS created_at
        FROM requests r
        JOIN request_place_aggregates rpa
          ON rpa.request_id = r.slug
        JOIN places p
          ON p.id = rpa.place_id
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

    interface TagAgg {
        String getExternal_id();
        String getTags_json();
    }

    @Query(value = """
        SELECT
            p.external_id AS external_id,
            CAST(
                COALESCE(jsonb_agg(DISTINCT t.tag), '[]'::jsonb)
                AS TEXT
            ) AS tags_json
        FROM requests r
        JOIN request_place_aggregates rpa
          ON rpa.request_id = r.slug
        JOIN places p
          ON p.id = rpa.place_id
        JOIN recommendation_notes rn
          ON rn.rpa_id = rpa.id
        LEFT JOIN LATERAL jsonb_array_elements_text(rn.tags) AS t(tag) ON TRUE
        WHERE r.slug = :slug
          AND p.external_id = ANY (:externalIds)
        GROUP BY p.external_id
        """, nativeQuery = true)
    List<TagAgg> findTagsAggByExternalIds(
            @Param("slug") String slug,
            @Param("externalIds") String[] externalIds
    );

    @Query(value = """
        SELECT p.external_id
        FROM requests r
        JOIN request_place_aggregates rpa ON rpa.request_id = r.slug
        JOIN places p ON p.id = rpa.place_id
        WHERE r.slug = :slug
        """, nativeQuery = true)
    List<String> findAllRecommendedExternalIds(@Param("slug") String slug);
}
