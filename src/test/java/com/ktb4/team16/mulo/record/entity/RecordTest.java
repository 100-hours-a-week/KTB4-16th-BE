package com.ktb4.team16.mulo.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ktb4.team16.mulo.music.entity.MusicTrack;
import com.ktb4.team16.mulo.place.entity.Place;
import com.ktb4.team16.mulo.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RecordTest {
    @Test
    void createsRecordWithCreationFields() {
        User user = mock(User.class);
        Place place = mock(Place.class);
        MusicTrack musicTrack = mock(MusicTrack.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 25, 12, 30);

        Record record = Record.create(
                user, place, musicTrack, Record.WeatherCondition.CLEAR,
                new BigDecimal("18.2"), (byte) 4, "좋았다", createdAt);

        assertThat(record.getUser()).isSameAs(user);
        assertThat(record.getPlace()).isSameAs(place);
        assertThat(record.getMusicTrack()).isSameAs(musicTrack);
        assertThat(record.getWeatherCondition()).isEqualTo(Record.WeatherCondition.CLEAR);
        assertThat(record.getTemperature()).isEqualByComparingTo("18.2");
        assertThat(record.getMoodScore()).isEqualTo((byte) 4);
        assertThat(record.getComment()).isEqualTo("좋았다");
        assertThat(record.getCreatedAt()).isEqualTo(createdAt);
    }
}
