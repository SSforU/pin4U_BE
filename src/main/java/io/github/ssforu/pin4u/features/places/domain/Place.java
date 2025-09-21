package io.github.ssforu.pin4u.features.places.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "places")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Place {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64, unique = true)
    private String externalId;

    @Column(name = "place_name", nullable = false, length = 100)
    private String placeName;

    @Column(name = "category_name", length = 64)
    private String categoryName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "road_address_name", length = 120)
    private String roadAddressName;

    @Column(name = "x", nullable = false, length = 50)
    private String x;

    @Column(name = "y", nullable = false, length = 50)
    private String y;

    @Column(name = "place_url", length = 200)
    private String placeUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        final OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
