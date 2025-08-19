package io.github.ssforu.pin4u.features.places.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.*;

@Entity
@Table(name = "place_mock")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PlaceMock {
    @Id
    @Column(name = "place_id")
    private Long id; // places.id와 동일 키

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(name = "rating", precision = 2, scale = 1)
    private BigDecimal rating; // 4.6 등

    @Column(name = "rating_count")
    private Integer ratingCount;

    // JSONB 매핑은 문자열로 두고 서비스/리포지토리에서 파싱
    @Column(name = "review_snippets", columnDefinition = "jsonb")
    private String reviewSnippets; // '["핸드드립 좋음", ...]'

    @Column(name = "image_urls", columnDefinition = "jsonb")
    private String imageUrls;

    // 영업시간(요청 사항)
    @Column(name = "opening_hours", length = 300)
    private String openingHours;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
