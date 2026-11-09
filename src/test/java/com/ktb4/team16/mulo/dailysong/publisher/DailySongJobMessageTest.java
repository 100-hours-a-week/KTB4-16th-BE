package com.ktb4.team16.mulo.dailysong.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

class DailySongJobMessageTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesAndDeserializesOnlyJobId() {
        String json = mapper.writeValueAsString(new DailySongJobMessage(42));

        assertThat(json).isEqualTo("{\"jobId\":42}");
        assertThat(mapper.readValue(json, DailySongJobMessage.class)).isEqualTo(new DailySongJobMessage(42));
    }

    @Test
    void rejectsMissingOrInvalidJobId() {
        assertThatThrownBy(() -> mapper.readValue("{}", DailySongJobMessage.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> new DailySongJobMessage(-1))
                .hasMessage("오늘의 노래 작업 ID는 양수여야 합니다.");
    }
}
