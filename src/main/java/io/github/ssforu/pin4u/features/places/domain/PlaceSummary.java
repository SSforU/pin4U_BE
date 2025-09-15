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

    @Column(name = "summary_text", length = 500) // ERD 기준: VARCHAR(500)
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


/*
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

    // TEXT 매핑
    @Column(name = "summary_text", columnDefinition = "text")
    private String summaryText;

    @Column(name = "place_name", length = 200)
    private String placeName;

    @Column(name = "road_address_name", length = 300)
    private String roadAddressName;

    @Column(name = "phone", length = 50)
    private String phone;

    // JSONB 문자열 보관
    @Column(name = "opening_hours", columnDefinition = "jsonb")
    private String openingHoursJson;

    @Column(name = "evidence", length = 200)
    private String evidence;

    // DB DEFAULT now() 사용
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}



*/
