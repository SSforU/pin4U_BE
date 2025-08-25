// src/main/java/io/github/ssforu/pin4u/features/recommendations/domain/RecommendationNote.java
package io.github.ssforu.pin4u.features.recommendations.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recommendation_notes"
//  ※ 동시성까지 잡으려면 DB에 UNIQUE(rpa_id, guest_id) 권장 (운영 DB 확인 필요)
// , uniqueConstraints = @UniqueConstraint(name = "uq_note_rpa_guest", columnNames = {"rpa_id","guest_id"})
)
public class RecommendationNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // request_place_aggregates.id
    @Column(name = "rpa_id", nullable = false)
    private Long rpaId;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "recommend_message", length = 300)
    private String recommendMessage;

    @Column(name = "image_url", length = 300)
    private String imageUrl;

    // JSONB ←→ List<String>
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_at_minute")
    private OffsetDateTime createdAtMinute; // DB 트리거가 세팅

    protected RecommendationNote() {}

    public RecommendationNote(Long rpaId,
                              String nickname,
                              String recommendMessage,
                              String imageUrl,
                              List<String> tags,
                              UUID guestId,
                              OffsetDateTime createdAt) {
        this.rpaId = rpaId;
        this.nickname = nickname;
        this.recommendMessage = recommendMessage;
        this.imageUrl = imageUrl;
        this.tags = tags;
        this.guestId = guestId;
        this.createdAt = createdAt;
    }

    /** created_at 누락 시 자동 세팅 */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Long getRpaId() { return rpaId; }
    public String getNickname() { return nickname; }
    public String getRecommendMessage() { return recommendMessage; }
    public String getImageUrl() { return imageUrl; }
    public List<String> getTags() { return tags; }
    public UUID getGuestId() { return guestId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCreatedAtMinute() { return createdAtMinute; }
}
