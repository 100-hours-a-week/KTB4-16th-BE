package com.ktb4.team16.mulo.record.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecordCreateRequestTest {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

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
    void validatesRequiredCreateFields() {
        RecordCreateRequest request = new RecordCreateRequest(
                new RecordCreateRequest.Location(
                        new BigDecimal("37.5"), new BigDecimal("127.0"), null, null),
                music(),
                0,
                null,
                1L
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingRequiredCreateFields() {
        RecordCreateRequest request = new RecordCreateRequest(null, null, null, null, null);

        Set<ConstraintViolation<RecordCreateRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(4);
    }

    @Test
    void rejectsMoodScoreOutsideRange() {
        RecordCreateRequest belowMinimum = new RecordCreateRequest(
                new RecordCreateRequest.Location(
                        new BigDecimal("37.5"), new BigDecimal("127.0"), null, null),
                music(),
                -51,
                null,
                1L
        );
        RecordCreateRequest aboveMaximum = new RecordCreateRequest(
                new RecordCreateRequest.Location(
                        new BigDecimal("37.5"), new BigDecimal("127.0"), null, null),
                music(),
                51,
                null,
                1L
        );

        assertThat(validator.validate(belowMinimum)).isNotEmpty();
        assertThat(validator.validate(aboveMaximum)).isNotEmpty();
    }

    @Test
    void rejectsCommentLongerThanEightyCharacters() {
        RecordCreateRequest request = new RecordCreateRequest(
                new RecordCreateRequest.Location(
                        new BigDecimal("37.5"), new BigDecimal("127.0"), null, null),
                music(),
                0,
                "a".repeat(81),
                1L
        );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void acceptsBothLegalDongFieldsWhenPresent() {
        RecordCreateRequest request = new RecordCreateRequest(
                new RecordCreateRequest.Location(
                        new BigDecimal("37.5"), new BigDecimal("127.0"),
                        "1168010100", "역삼동"),
                music(),
                0,
                null,
                1L
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsOnlyLegalDongCodeWhenNameIsMissing() {
        RecordCreateRequest request = new RecordCreateRequest(
                new RecordCreateRequest.Location(
                        new BigDecimal("37.5"), new BigDecimal("127.0"),
                        "1168010100", null),
                music(),
                0,
                null,
                1L
        );

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getMessage().equals("INVALID_INPUT_VALUE"));
    }

    @Test
    void rejectsOnlyLegalDongNameWhenCodeIsMissing() {
        RecordCreateRequest request = new RecordCreateRequest(
                new RecordCreateRequest.Location(
                        new BigDecimal("37.5"), new BigDecimal("127.0"),
                        null, "역삼동"),
                music(),
                0,
                null,
                1L
        );

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getMessage().equals("INVALID_INPUT_VALUE"));
    }

    private RecordCreateRequest.Music music() {
        return new RecordCreateRequest.Music(
                "track-id", "title", "artist", "album-image", "external-url");
    }
}
