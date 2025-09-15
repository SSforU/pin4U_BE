// src/main/java/io/github/ssforu/pin4u/features/member/domain/User.java
package io.github.ssforu.pin4u.features.member.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // ★ DB: BIGSERIAL과 일치
    private Long id;

    @Column(name = "kakao_user_id")                       // ★ v17 컬럼과 일치
    private Long kakaoUserId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "preference_text", nullable = false, length = 200)
    private String preferenceText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    public User(Long kakaoUserId, String nickname, String preferenceText) {
        this.kakaoUserId = kakaoUserId;
        this.nickname = nickname;
        this.preferenceText = preferenceText == null ? "" : preferenceText;
    }

    public Long getId() { return id; }
    public Long getKakaoUserId() { return kakaoUserId; }
    public String getNickname() { return nickname; }
    public String getPreferenceText() { return preferenceText; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setNickname(String nickname) { this.nickname = nickname; }
}



/*
package io.github.ssforu.pin4u.features.member.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    private Long id;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "preference_text", nullable = false, length = 200)
    private String preferenceText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}
    public User(Long id, String nickname, String preferenceText) {
        this.id = id; this.nickname = nickname; this.preferenceText = preferenceText;
    }

    public Long getId() { return id; }
    public String getNickname() { return nickname; }
    public String getPreferenceText() { return preferenceText; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
*/
