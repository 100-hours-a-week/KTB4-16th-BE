package com.ktb4.team16.mulo.recommendation.entity;

import com.ktb4.team16.mulo.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recommendation_playlists")
@Getter
@NoArgsConstructor
public class RecommendationPlaylist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recommendationPlaylistId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime createdAt;

    private RecommendationPlaylist(User user, LocalDateTime createdAt) {
        this.user = user;
        this.createdAt = createdAt;
    }

    // 새 현재 추천 플레이리스트의 영속 상태를 생성한다.
    public static RecommendationPlaylist create(User user, LocalDateTime createdAt) {
        return new RecommendationPlaylist(user, createdAt);
    }
}
