package io.github.ssforu.pin4u.features.groups.infra;

import io.github.ssforu.pin4u.features.places.domain.Place;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface GroupMapQueryRepository extends Repository<Place, Long> {

    interface Row {
        String getExternal_id();
        String getId_stripped();
        String getPlace_name();
        String getCategory_group_code();
        String getCategory_group_name();
        String getCategory_name();
        String getAddress_name();
        String getRoad_address_name();
        String getX();
        String getY();
        String getPlace_url();
        Integer getRecommended_count();
        Integer getDistance_m();
        Double getMock_rating();
        Integer getMock_rating_count();
        String getMock_image_urls_json();
        String getMock_opening_hours_json();
        String getMock_review_snippets_json();
        String getAi_summary_text();
        String getAi_evidence_json();
        OffsetDateTime getAi_updated_at();
    }

    @Query(value = """
        SELECT
            p.external_id                                           AS external_id,
            split_part(p.external_id, ':', 2)                       AS id_stripped,
            p.place_name                                            AS place_name,
            p.category_group_code                                   AS category_group_code,
            p.category_group_name                                   AS category_group_name,
            p.category_name                                         AS category_name,
            p.address_name                                          AS address_name,
            p.road_address_name                                     AS road_address_name,
            CAST(p.x AS TEXT)                                       AS x,
            CAST(p.y AS TEXT)                                       AS y,
            p.place_url                                             AS place_url,
            SUM(rpa.recommended_count)::INT                         AS recommended_count,
            ROUND(
              6371000 * 2 * ASIN(
                SQRT(
                  POWER(SIN(RADIANS( ((p.y)::double precision - :centerLat) / 2 )), 2) +
                  COS(RADIANS(:centerLat)) * COS(RADIANS((p.y)::double precision)) *
                  POWER(SIN(RADIANS( ((p.x)::double precision - :centerLng) / 2 )), 2)
                )
              )
            )::INT                                                  AS distance_m,
            pm.rating                                               AS mock_rating,
            pm.rating_count                                         AS mock_rating_count,
            CAST(pm.image_urls AS TEXT)                             AS mock_image_urls_json,
            CAST(pm.opening_hours AS TEXT)                          AS mock_opening_hours_json,
            CAST(pm.review_snippets AS TEXT)                        AS mock_review_snippets_json,
            NULL::TEXT                                              AS ai_summary_text,
            NULL::TEXT                                              AS ai_evidence_json,
            NULL::timestamptz                                       AS ai_updated_at
        FROM groups g
        JOIN requests r                 ON r.group_id = g.id
        JOIN request_place_aggregates rpa ON rpa.request_id = r.slug
        JOIN places p                   ON p.id = rpa.place_id
        LEFT JOIN place_mock pm         ON pm.external_id = p.external_id
        WHERE g.id = :groupId
        GROUP BY
            p.external_id, p.place_name, p.category_group_code, p.category_group_name, p.category_name,
            p.address_name, p.road_address_name, p.x, p.y, p.place_url, pm.rating, pm.rating_count,
            pm.image_urls, pm.opening_hours, pm.review_snippets
        ORDER BY recommended_count DESC, distance_m ASC
        LIMIT :limit
    """, nativeQuery = true)
    List<Row> findItemsByGroupId(
            @Param("groupId") Long groupId,
            @Param("centerLat") double centerLat,
            @Param("centerLng") double centerLng,
            @Param("limit") int limit
    );
}
