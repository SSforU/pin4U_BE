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

    @Id
    @Column(name = "external_id", nullable = false, length = 80)
    private String externalId;

    @Column(name = "rating", precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "rating_count")
    private Integer ratingCount;

    @Column(name = "review_snippets", columnDefinition = "jsonb")
    private String reviewSnippetsJson;

    @Column(name = "image_urls", columnDefinition = "jsonb")
    private String imageUrlsJson;

    @Column(name = "opening_hours", columnDefinition = "jsonb")
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



/*
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

    @Id
    @Column(name = "external_id", nullable = false, length = 100) // 80 → 100
    private String externalId;

    @Column(name = "rating", precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "rating_count")
    private Integer ratingCount;

    // DB jsonb를 문자열로 보관 (서비스에서 JSON 파싱)
    @Column(name = "review_snippets", columnDefinition = "jsonb")
    private String reviewSnippetsJson;

    @Column(name = "image_urls", columnDefinition = "jsonb")
    private String imageUrlsJson;

    @Column(name = "opening_hours", columnDefinition = "jsonb")
    private String openingHoursJson;

    // DB DEFAULT now() 사용 (NOT NULL 위반 방지)
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}



*/
