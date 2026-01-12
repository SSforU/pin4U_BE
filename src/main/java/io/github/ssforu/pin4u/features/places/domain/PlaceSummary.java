// src/main/java/io/github/ssforu/pin4u/features/places/domain/PlaceSummary.java
package io.github.ssforu.pin4u.features.places.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "place_summaries")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PlaceSummary {
    @Id
    @Column(name = "place_id")
    private Long id; // places.id와 동일 키

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "place_id")
    private Place place;

    // V13 스키마: TEXT
    @Column(name = "summary_text", columnDefinition = "text")
    private String summaryText;

    @Column(name = "evidence", length = 200)
    private String evidence;

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
