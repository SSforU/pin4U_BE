package io.github.ssforu.pin4u.features.requests.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "request_place_aggregates",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_req_place",
                columnNames = {"request_id", "place_id"}
        )
)
public class RequestPlaceAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "recommended_count", nullable = false)
    private int recommendedCount;

    @Column(name = "first_recommended_at", nullable = false)
    private OffsetDateTime firstRecommendedAt;

    @Column(name = "last_recommended_at", nullable = false)
    private OffsetDateTime lastRecommendedAt;

    protected RequestPlaceAggregate() {}

    public RequestPlaceAggregate(String requestId, Long placeId) {
        this.requestId = requestId;
        this.placeId = placeId;
    }

    @PrePersist
    protected void onCreate() {
        if (this.recommendedCount < 0) this.recommendedCount = 0;
        OffsetDateTime now = OffsetDateTime.now();
        if (this.firstRecommendedAt == null) this.firstRecommendedAt = now;
        if (this.lastRecommendedAt == null)  this.lastRecommendedAt  = now;
    }

    public void increaseCount() {
        if (this.recommendedCount < Integer.MAX_VALUE) {
            this.recommendedCount += 1;
        }
        this.lastRecommendedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public Long getPlaceId() { return placeId; }
    public int getRecommendedCount() { return recommendedCount; }
    public OffsetDateTime getFirstRecommendedAt() { return firstRecommendedAt; }
    public OffsetDateTime getLastRecommendedAt() { return lastRecommendedAt; }
}
