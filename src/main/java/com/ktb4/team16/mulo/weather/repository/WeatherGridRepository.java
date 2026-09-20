package com.ktb4.team16.mulo.weather.repository;

import com.ktb4.team16.mulo.weather.entity.WeatherGrid;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherGridRepository extends JpaRepository<WeatherGrid, Long> {
    Optional<WeatherGrid> findByGridXAndGridY(short gridX, short gridY);
}
