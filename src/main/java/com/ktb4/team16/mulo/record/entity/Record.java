package com.ktb4.team16.mulo.record.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.music.entity.MusicTrack;

@Entity
@Table(name = "records")
@Getter
@NoArgsConstructor
public class Record {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "music_track_id", nullable = false)
    private MusicTrack musicTrack;

    @Enumerated(EnumType.STRING)
    private WeatherCondition weatherCondition;

    @Column(precision = 3, scale = 1)
    private BigDecimal temperature;

    @Column(nullable = false)
    private Byte moodScore;

    @Column(length = 80)
    private String comment;  

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    public enum WeatherCondition {
        CLEAR,
        CLOUDY,
        OVERCAST,
        RAIN,
        SNOW,
        RAIN_SNOW,
        SHOWER
    }
}
