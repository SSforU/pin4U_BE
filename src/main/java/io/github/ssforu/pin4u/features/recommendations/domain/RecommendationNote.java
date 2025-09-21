package io.github.ssforu.pin4u.features.recommendations.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recommendation_notes")
public class RecommendationNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rpa_id", nullable = false)
    private Long rpaId;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "recommend_message", length = 300)
    private String recommendMessage;

    @Column(name = "image_url", length = 300)
    private String imageUrl;

    @Column(name = "image_is_public", nullable = false)
    private boolean imageIsPublic = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_at_minute")
    private OffsetDateTime createdAtMinute;

    protected RecommendationNote() {}

    public RecommendationNote(Long rpaId,
                              String nickname,
                              String recommendMessage,
                              String imageUrl,
                              boolean imageIsPublic,
                              List<String> tags,
                              UUID guestId,
                              OffsetDateTime createdAt) {
        this.rpaId = rpaId;
        this.nickname = nickname;
        this.recommendMessage = recommendMessage;
        this.imageUrl = imageUrl;
        this.imageIsPublic = imageIsPublic;
        this.tags = tags;
        this.guestId = guestId;
        this.createdAt = createdAt;
    }

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
    public boolean isImageIsPublic() { return imageIsPublic; }
    public void setImageIsPublic(boolean imageIsPublic) { this.imageIsPublic = imageIsPublic; }
    public List<String> getTags() { return tags; }
    public UUID getGuestId() { return guestId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getCreatedAtMinute() { return createdAtMinute; }
}
