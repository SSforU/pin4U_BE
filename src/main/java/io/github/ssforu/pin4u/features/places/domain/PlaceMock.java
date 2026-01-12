package io.github.ssforu.pin4u.features.places.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "place_mock")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceMock {

    // PK: places.external_id (VARCHAR(100))
    @Id
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    // NUMERIC(2,1) ↔ BigDecimal 매핑
    @Column(name = "rating", precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "rating_count")
    private Integer ratingCount;

    // V19에서 DB 타입은 text. 애플리케이션은 JSON 문자열을 그대로 넣고/읽는다.
    @Column(name = "review_snippets")
    private String reviewSnippetsJson;

    @Column(name = "image_urls")
    private String imageUrlsJson;

    @Column(name = "opening_hours")
    private String openingHoursJson;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
