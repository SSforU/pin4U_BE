package io.github.ssforu.pin4u.features.requests.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "requests",
        indexes = {
                @Index(name = "idx_requests_slug", columnList = "slug"),
                @Index(name = "idx_requests_group_id", columnList = "group_id"),
                @Index(name = "idx_requests_station_code", columnList = "station_code")
        }
)
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "slug", nullable = false, length = 64, unique = true)
    private String slug;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "station_code", nullable = false, length = 20)
    private String stationCode;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "request_message", nullable = false, length = 500)
    private String requestMessage;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected Request() {}

    public Request(String slug, Long ownerUserId, String stationCode, Long groupId, String requestMessage) {
        this.slug = slug;
        this.ownerUserId = ownerUserId;
        this.stationCode = stationCode;
        this.groupId = groupId;
        this.requestMessage = requestMessage;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getStationCode() { return stationCode; }
    public Long getGroupId() { return groupId; }
    public String getRequestMessage() { return requestMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}