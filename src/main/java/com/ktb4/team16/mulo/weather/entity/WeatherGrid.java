package com.ktb4.team16.mulo.weather.entity;

import com.ktb4.team16.mulo.weather.domain.GridCoordinate;
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
@Table(name = "weather_grids")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherGrid {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "weather_grid_id")
    private Long weatherGridId;

    @Column(name = "grid_x", nullable = false)
    private short gridX;

    @Column(name = "grid_y", nullable = false)
    private short gridY;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private WeatherGrid(short gridX, short gridY) {
        this.gridX = gridX;
        this.gridY = gridY;
    }

    public static WeatherGrid create(GridCoordinate coordinate) {
        return new WeatherGrid(coordinate.x(), coordinate.y());
    }
}
