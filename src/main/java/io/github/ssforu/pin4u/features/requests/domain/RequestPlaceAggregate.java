package io.github.ssforu.pin4u.features.requests.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "request_place_aggregates",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_req_place",                     // [FIX] 명확한 이름
                columnNames = {"request_id", "place_id"}   // [FIX] external_id → place_id
        )
)
public class RequestPlaceAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 요청 식별자는 slug(문자열)
    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    // [FIX] 외부키: places.id 를 FK로 사용
    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "recommended_count", nullable = false)
    private int recommendedCount;

    @Column(name = "first_recommended_at", nullable = false)
    private OffsetDateTime firstRecommendedAt;

    @Column(name = "last_recommended_at", nullable = false)
    private OffsetDateTime lastRecommendedAt;

    protected RequestPlaceAggregate() {}

    // [FIX] 생성자 시그니처를 (slug, placeId)로 통일
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

    // getters
    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public Long getPlaceId() { return placeId; }        // [FIX]
    public int getRecommendedCount() { return recommendedCount; }
    public OffsetDateTime getFirstRecommendedAt() { return firstRecommendedAt; }
    public OffsetDateTime getLastRecommendedAt() { return lastRecommendedAt; }
}



/* 2차
// src/main/java/io/github/ssforu/pin4u/features/requests/domain/RequestPlaceAggregate.java
package io.github.ssforu.pin4u.features.requests.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "request_place_aggregates",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_req_place",              // ★ DB 인덱스명과 의미 일치
                columnNames = {"request_id", "place_id"}
        )
)
public class RequestPlaceAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ★ DB: VARCHAR(100) → requests.slug FK
    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    // ★ DB: BIGINT → places.id FK
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

    */
/** 최초 persist 직전 안전 보정 *//*

    @PrePersist
    protected void onCreate() {
        if (this.recommendedCount < 0) this.recommendedCount = 0;
        OffsetDateTime now = OffsetDateTime.now();
        if (this.firstRecommendedAt == null) this.firstRecommendedAt = now;
        if (this.lastRecommendedAt == null)  this.lastRecommendedAt  = now;
    }

    */
/** 추천 1 증가 (과도 증가 방지) *//*

    public void increaseCount() {
        if (this.recommendedCount < Integer.MAX_VALUE) {
            this.recommendedCount += 1;
        }
        this.lastRecommendedAt = OffsetDateTime.now();
    }

    // getters
    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public Long getPlaceId() { return placeId; }
    public int getRecommendedCount() { return recommendedCount; }
    public OffsetDateTime getFirstRecommendedAt() { return firstRecommendedAt; }
    public OffsetDateTime getLastRecommendedAt() { return lastRecommendedAt; }
}
*/





/*

// src/main/java/io/github/ssforu/pin4u/features/requests/domain/RequestPlaceAggregate.java
package io.github.ssforu.pin4u.features.requests.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "request_place_aggregates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_rpa_request_place",
                columnNames = {"request_id", "place_external_id"}
        )
)
public class RequestPlaceAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 요청 식별자는 slug(문자열)로 운용 (v6: slug 64자)
    @Column(name = "request_id", nullable = false, length = 64) // ← 문서화 목적(스키마는 Flyway)
    private String requestId;

    // places.external_id = "kakao:123..." (Place 엔티티 64자)
    @Column(name = "place_external_id", nullable = false, length = 64) // ← 문서화 목적
    private String placeExternalId;

    @Column(name = "recommended_count", nullable = false)
    private int recommendedCount;

    @Column(name = "first_recommended_at", nullable = false)
    private OffsetDateTime firstRecommendedAt;

    @Column(name = "last_recommended_at", nullable = false)
    private OffsetDateTime lastRecommendedAt;

    protected RequestPlaceAggregate() {}

    public RequestPlaceAggregate(String requestId, String placeExternalId) {
        this.requestId = requestId;
        this.placeExternalId = placeExternalId;
        // 나머지는 @PrePersist에서 보정
    }

    */
/** 최초 persist 직전 안전 보정 *//*

    @PrePersist
    protected void onCreate() {
        if (this.recommendedCount < 0) this.recommendedCount = 0;
        OffsetDateTime now = OffsetDateTime.now();
        if (this.firstRecommendedAt == null) this.firstRecommendedAt = now;
        if (this.lastRecommendedAt == null)  this.lastRecommendedAt  = now;
    }

    */
/** 추천 1 증가 (과도 증가 방지) *//*

    public void increaseCount() {
        // int 오버플로 방지용 가드 (이론상)
        if (this.recommendedCount < Integer.MAX_VALUE) {
            this.recommendedCount += 1;
        }
        this.lastRecommendedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public String getPlaceExternalId() { return placeExternalId; }
    public int getRecommendedCount() { return recommendedCount; }
    public OffsetDateTime getFirstRecommendedAt() { return firstRecommendedAt; }
    public OffsetDateTime getLastRecommendedAt() { return lastRecommendedAt; }
}
*/
