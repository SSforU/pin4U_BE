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
    private String externalId;     // ex) "kakao:12345"

    @Column(name = "place_name", nullable = false, length = 100)
    private String placeName;

    @Column(name = "category_name", length = 64)
    private String categoryName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "road_address_name", length = 120)
    private String roadAddressName;

    @Column(name = "x", nullable = false, length = 50) // Kakao: x=lng
    private String x;

    @Column(name = "y", nullable = false, length = 50) // Kakao: y=lat
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


/*package io.github.ssforu.pin4u.features.places.domain;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ex) "kakao:12345"
    @Column(name = "external_id", nullable = false, length = 100, unique = true)
    private String externalId;

    // Kakao 원본 place id (선택 컬럼, DB에는 존재)
    @Column(name = "kakao_id", length = 50)
    private String kakaoId;

    @Column(name = "place_name", nullable = false, length = 200)
    private String placeName;

    @Column(name = "category_group_code", length = 10)
    private String categoryGroupCode;

    @Column(name = "category_group_name", length = 50)
    private String categoryGroupName;

    @Column(name = "category_name", length = 300)
    private String categoryName;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "address_name", length = 300)
    private String addressName;

    @Column(name = "road_address_name", length = 300)
    private String roadAddressName;

    // Kakao: x=lng, y=lat
    @Column(name = "x", nullable = false, length = 50)
    private String x;

    @Column(name = "y", nullable = false, length = 50)
    private String y;

    @Column(name = "place_url", length = 500)
    private String placeUrl;

    // DB DEFAULT now() 사용 (NOT NULL 위반 방지)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}*/




/*
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
}
   */
/* //  수정된 내용 Kakao x=lng, y=lat (NUMERIC(11,7))
    @Column(name = "x", nullable = false, precision = 11, scale = 7)
    private BigDecimal x;

    @Column(name = "y", nullable = false, precision = 11, scale = 7)
    private BigDecimal y;수정된 내용 끝*//*

//*/
