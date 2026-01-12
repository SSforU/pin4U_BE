// src/main/java/io/github/ssforu/pin4u/features/places/domain/Place.java
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

    // V13 스키마: length 100, UNIQUE, NULL 허용
    @Column(name = "external_id", length = 100, unique = true)
    private String externalId;

    @Column(name = "place_name", nullable = false, length = 200)
    private String placeName;

    // ★ 추가: 그룹 코드/이름, 풀 카테고리
    @Column(name = "category_group_code", length = 10)
    private String categoryGroupCode;

    @Column(name = "category_group_name", length = 50)
    private String categoryGroupName;

    @Column(name = "category_name", length = 300)
    private String categoryName;

    @Column(name = "phone", length = 50)
    private String phone;

    // ★ 추가: 지번 주소
    @Column(name = "address_name", length = 300)
    private String addressName;

    @Column(name = "road_address_name", length = 300)
    private String roadAddressName;

    @Column(name = "x", nullable = false, length = 50)
    private String x;   // 카카오 원본 문자열

    @Column(name = "y", nullable = false, length = 50)
    private String y;   // 카카오 원본 문자열

    @Column(name = "place_url", length = 500)
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
