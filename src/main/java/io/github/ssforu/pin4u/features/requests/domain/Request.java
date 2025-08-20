// src/main/java/io/github/ssforu/pin4u/features/requests/domain/Request.java
package io.github.ssforu.pin4u.features.requests.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "requests") // ✅ 테이블명 일치
public class Request {

    @Id
    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    @Column(name = "owner_nickname", nullable = false, length = 50)
    private String ownerNickname;

    @Column(name = "station_code", nullable = false, length = 20)
    private String stationCode;

    @Column(name = "request_message", nullable = false, length = 500)
    private String requestMessage;

    @Column(name = "recommend_count", nullable = false)
    private int recommendCount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Request() { } // for JPA

    public Request(String slug, String ownerNickname, String stationCode, String requestMessage) {
        this.slug = slug;
        this.ownerNickname = ownerNickname;
        this.stationCode = stationCode;
        this.requestMessage = requestMessage;
        this.recommendCount = 0;
        this.createdAt = OffsetDateTime.now();
    }

    // getters ...
    public String getSlug() { return slug; }
    public String getOwnerNickname() { return ownerNickname; }
    public String getStationCode() { return stationCode; }
    public String getRequestMessage() { return requestMessage; }
    public int getRecommendCount() { return recommendCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
