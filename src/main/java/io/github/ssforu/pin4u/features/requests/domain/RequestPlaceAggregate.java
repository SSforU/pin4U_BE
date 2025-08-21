// src/main/java/io/github/ssforu/pin4u/features/requests/domain/RequestPlaceAggregate.java
package io.github.ssforu.pin4u.features.requests.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "request_place_aggregates",
        uniqueConstraints = @UniqueConstraint(name = "uq_rpa_request_place",
                columnNames = {"request_id", "place_external_id"}))
public class RequestPlaceAggregate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 요청 식별자는 slug(문자열)로 운용 (v12 마이그레이션 기준)
    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "place_external_id", nullable = false, length = 100)
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
        this.recommendedCount = 0;
        var now = OffsetDateTime.now();
        this.firstRecommendedAt = now;
        this.lastRecommendedAt = now;
    }

    public void increaseCount() {
        this.recommendedCount += 1;
        this.lastRecommendedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public String getPlaceExternalId() { return placeExternalId; }
    public int getRecommendedCount() { return recommendedCount; }
    public OffsetDateTime getFirstRecommendedAt() { return firstRecommendedAt; }
    public OffsetDateTime getLastRecommendedAt() { return lastRecommendedAt; }
}
