package io.github.ssforu.pin4u.features.places.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "place_summaries")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PlaceSummary {
    @Id
    @Column(name = "place_id")
    private Long id; // places.id와 동일 키

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(name = "summary_text", length = 500)
    private String summaryText;

    // evidence에 태그/카테고리/평점 등 근거를 한 줄 JSON 또는 문자열로 저장
    @Column(name = "evidence", length = 200)
    private String evidence;

    // preference_hash는 보류(요청에 따라 제거했음)

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
