package com.ktb4.team16.mulo.place.repository;

import com.ktb4.team16.mulo.place.entity.Place;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    Optional<Place> findByLatitudeAndLongitude(BigDecimal latitude, BigDecimal longitude);
}
