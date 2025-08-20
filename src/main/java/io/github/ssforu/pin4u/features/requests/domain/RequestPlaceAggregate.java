package io.github.ssforu.pin4u.features.requests.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "request_place_aggregates",
        uniqueConstraints = @UniqueConstraint(name = "uk_request_place", columnNames = {"request_id", "place_id"}))
public class RequestPlaceAggregate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id", nullable = false) private Long requestId;
    @Column(name = "place_id",   nullable = false) private Long placeId;
    @Column(name = "recommend_count", nullable = false) private Long recommendCount;
    @Column(name = "first_recommended_at") private OffsetDateTime firstRecommendedAt;
    @Column(name = "last_recommended_at")  private OffsetDateTime lastRecommendedAt;
    protected RequestPlaceAggregate() {}
    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public Long getPlaceId() { return placeId; }
    public Long getRecommendCount() { return recommendCount; }
    public OffsetDateTime getFirstRecommendedAt() { return firstRecommendedAt; }
    public OffsetDateTime getLastRecommendedAt() { return lastRecommendedAt; }
}
