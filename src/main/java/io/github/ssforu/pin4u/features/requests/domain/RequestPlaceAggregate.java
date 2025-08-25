// src/main/java/io/github/ssforu/pin4u/features/requests/domain/RequestPlaceAggregate.java
package io.github.ssforu.pin4u.features.requests.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "request_place_aggregates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_rpa_request_place",
                columnNames = {"request_id", "place_external_id"}
        )
)
public class RequestPlaceAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 요청 식별자는 slug(문자열)로 운용 (v6: slug 64자)
    @Column(name = "request_id", nullable = false, length = 64) // ← 문서화 목적(스키마는 Flyway)
    private String requestId;

    // places.external_id = "kakao:123..." (Place 엔티티 64자)
    @Column(name = "place_external_id", nullable = false, length = 64) // ← 문서화 목적
    private String placeExternalId;

    @Column(name = "recommended_count", nullable = false)
    private int recommendedCount;

    @Column(name = "first_recommended_at", nullable = false)
    private OffsetDateTime firstRecommendedAt;

    @Column(name = "last_recommended_at", nullable = false)
    private OffsetDateTime lastRecommendedAt;

    protected RequestPlaceAggregate() {}

    public RequestPlaceAggregate(String requestId, String placeExternalId) {
        this.requestId = requestId;
        this.placeExternalId = placeExternalId;
        // 나머지는 @PrePersist에서 보정
    }

    /** 최초 persist 직전 안전 보정 */
    @PrePersist
    protected void onCreate() {
        if (this.recommendedCount < 0) this.recommendedCount = 0;
        OffsetDateTime now = OffsetDateTime.now();
        if (this.firstRecommendedAt == null) this.firstRecommendedAt = now;
        if (this.lastRecommendedAt == null)  this.lastRecommendedAt  = now;
    }

    /** 추천 1 증가 (과도 증가 방지) */
    public void increaseCount() {
        // int 오버플로 방지용 가드 (이론상)
        if (this.recommendedCount < Integer.MAX_VALUE) {
            this.recommendedCount += 1;
        }
        this.lastRecommendedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public String getPlaceExternalId() { return placeExternalId; }
    public int getRecommendedCount() { return recommendedCount; }
    public OffsetDateTime getFirstRecommendedAt() { return firstRecommendedAt; }
    public OffsetDateTime getLastRecommendedAt() { return lastRecommendedAt; }
}
