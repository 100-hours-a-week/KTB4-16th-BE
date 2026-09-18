package com.ktb4.team16.mulo.user.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 254, insertable = false, updatable = false)
    private String activeEmail;

    @Column(nullable = false, length = 60)
    private String passwordHash;

    @Column(nullable = false, length = 10)
    private String nickname;

    @Column(length = 10, insertable = false, updatable = false)
    private String activeNickname;

    @Column(length = 50)
    private String musicGenre;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
