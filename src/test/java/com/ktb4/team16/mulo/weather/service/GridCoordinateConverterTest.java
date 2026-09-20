package com.ktb4.team16.mulo.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.weather.domain.GridCoordinate;
import org.junit.jupiter.api.Test;

class GridCoordinateConverterTest {
    private final GridCoordinateConverter converter = new GridCoordinateConverter();

    @Test
    void convertsSeoulCoordinateToKmaGrid() {
        assertThat(converter.convert(37.5665, 126.9780))
                .isEqualTo(new GridCoordinate((short) 60, (short) 127));
    }
}
