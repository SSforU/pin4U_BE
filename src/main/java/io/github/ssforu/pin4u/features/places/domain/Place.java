package io.github.ssforu.pin4u.features.places.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
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

    // ❌ 삭제: external_source는 더 이상 사용하지 않음
    // @Column(name = "external_source", nullable = false, length = 16)
    // private String externalSource;

    @Column(name = "external_id", nullable = false, length = 64, unique = true)
    private String externalId;     // ex) "kakao:12345"

    @Column(name = "place_name", nullable = false, length = 100)
    private String placeName;

    @Column(name = "category_name", length = 64)
    private String categoryName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "road_address_name", length = 120)
    private String roadAddressName;

    // Kakao x=lng, y=lat (NUMERIC(11,7))
    @Column(name = "x", nullable = false, precision = 11, scale = 7)
    private BigDecimal x;

    @Column(name = "y", nullable = false, precision = 11, scale = 7)
    private BigDecimal y;

    @Column(name = "place_url", length = 200)
    private String placeUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}