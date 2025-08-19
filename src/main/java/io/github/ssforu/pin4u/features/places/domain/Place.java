package io.github.ssforu.pin4u.features.places.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "places")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Place {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_source", nullable = false, length = 16)
    private String externalSource; // 'kakao' | 'mock'

    @Column(name = "external_id", nullable = false, length = 64, unique = true)
    private String externalId; // ex) "kakao:12345"

    @Column(name = "place_name", nullable = false, length = 100)
    private String placeName;

    @Column(name = "category_group_code", length = 8)
    private String categoryGroupCode;

    @Column(name = "category_group_name", length = 32)
    private String categoryGroupName;

    @Column(name = "category_name", length = 64)
    private String categoryName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "road_address_name", length = 120)
    private String roadAddressName;

    // Kakao의 x=경도, y=위도 (NUMERIC(11,7))
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
