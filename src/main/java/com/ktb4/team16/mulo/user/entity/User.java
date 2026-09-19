package com.ktb4.team16.mulo.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 254)
    private String email;

    // 활성 사용자 전용 UNIQUE Generated Column은 DB가 계산한다.
    @Column(name = "active_email", length = 254, insertable = false, updatable = false)
    private String activeEmail;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(nullable = false, length = 10)
    private String nickname;

    @Column(name = "active_nickname", length = 10, insertable = false, updatable = false)
    private String activeNickname;

    @Column(name = "music_genre", length = 50)
    private String musicGenre;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // soft delete된 사용자는 이메일·닉네임 중복 조회에서 제외한다.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private User(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }

    public static User signup(String email, String passwordHash, String nickname) {
        return new User(email, passwordHash, nickname);
    }
}
