package io.github.ssforu.pin4u.features.requests.infra;

import io.github.ssforu.pin4u.features.places.domain.Place;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * #7 요청 상세(지도 핀 + 카드뉴스) 읽기 전용 Native 쿼리.
 * 정렬: recommended_count DESC → distance_m ASC
 */
public interface RequestDetailQueryRepository extends Repository<Place, Long> {

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
        Integer getDistance_m();
        String getPlace_url();
        Integer getRecommended_count();

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
        rpa.recommended_count                                   AS recommended_count,
        ROUND(
          6371000 * 2 * ASIN(
            SQRT(
              POWER(SIN(RADIANS( ((p.y)::double precision - (s.lat)::double precision) / 2 )), 2) +
              COS(RADIANS((s.lat)::double precision)) * COS(RADIANS((p.y)::double precision)) *
              POWER(SIN(RADIANS( ((p.x)::double precision - (s.lng)::double precision) / 2 )), 2)
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
    FROM requests r
    JOIN stations s               ON s.code = r.station_code
    JOIN request_place_aggregates rpa ON rpa.request_id::text = r.slug        
    JOIN places p                 ON p.external_id = rpa.place_external_id
    LEFT JOIN place_mock pm       ON pm.external_id = p.external_id
    WHERE r.slug = :slug
    ORDER BY rpa.recommended_count DESC, distance_m ASC
    LIMIT :limit
    """, nativeQuery = true)
    List<Row> findItemsBySlug(@Param("slug") String slug, @Param("limit") int limit);


}
