package com.ktb4.team16.mulo.recorddraft.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RecordDraftUpsertRequestTest {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsAllOptionalFieldsAsNull() {
        assertThat(validator.validate(new RecordDraftUpsertRequest(
                null, null, null, null, null))).isEmpty();
    }

    @Test
    void validatesRequiredLocationFieldsWhenLocationIsPresent() {
        RecordDraftUpsertRequest request = request(
                new RecordDraftUpsertRequest.Location(null, BigDecimal.ZERO, null, null),
                null, null, null, null);

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("location.latitude"));
    }

    @Test
    void rejectsCoordinatesOutsideRecordCreateRange() {
        RecordDraftUpsertRequest request = request(
                new RecordDraftUpsertRequest.Location(
                        new BigDecimal("90.0000001"), BigDecimal.ZERO, null, null),
                null, null, null, null);

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("location.latitude")
                        && violation.getMessage().equals("INVALID_LATITUDE"));
    }

    @Test
    void validatesMusicFieldsWhenMusicIsPresent() {
        RecordDraftUpsertRequest request = request(null,
                new RecordDraftUpsertRequest.Music("track", null,
                        "artist", "album", "external"),
                null, null, null);

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("music.title"));
    }

    @Test
    void rejectsMusicFieldsLongerThanDatabaseColumns() {
        RecordDraftUpsertRequest request = request(null,
                new RecordDraftUpsertRequest.Music("t".repeat(23), "title",
                        "artist", "album", "external"),
                null, null, null);

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("music.externalTrackId"));
    }

    @Test
    void validatesMoodAndCommentLimits() {
        RecordDraftUpsertRequest moodOutOfRange = request(null, null, 51, null, null);
        RecordDraftUpsertRequest commentTooLong = request(null, null, null, "c".repeat(81), null);

        assertThat(validator.validate(moodOutOfRange)).isNotEmpty();
        assertThat(validator.validate(commentTooLong)).isNotEmpty();
    }

    @Test
    void omittedAndExplicitNullFieldsBothDeserializeAsNull() throws Exception {
        RecordDraftUpsertRequest omitted = objectMapper.readValue("{}",
                RecordDraftUpsertRequest.class);
        RecordDraftUpsertRequest explicitNull = objectMapper.readValue("""
                {"location":null,"music":null,"moodScore":null,"comment":null,"uploadId":null}
                """, RecordDraftUpsertRequest.class);

        assertAllFieldsNull(omitted);
        assertAllFieldsNull(explicitNull);
    }

    private void assertAllFieldsNull(RecordDraftUpsertRequest request) {
        assertThat(request.location()).isNull();
        assertThat(request.music()).isNull();
        assertThat(request.moodScore()).isNull();
        assertThat(request.comment()).isNull();
        assertThat(request.uploadId()).isNull();
    }

    private RecordDraftUpsertRequest request(
            RecordDraftUpsertRequest.Location location,
            RecordDraftUpsertRequest.Music music,
            Integer moodScore,
            String comment,
            Long uploadId
    ) {
        return new RecordDraftUpsertRequest(location, music, moodScore, comment, uploadId);
    }
}
