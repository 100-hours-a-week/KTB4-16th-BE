package com.ktb4.team16.mulo.record.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RecordCommentUpdateRequestTest {
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
    void distinguishesMissingCommentFromExplicitNull() throws Exception {
        RecordCommentUpdateRequest missingComment = objectMapper.readValue(
                "{}", RecordCommentUpdateRequest.class);
        RecordCommentUpdateRequest nullComment = objectMapper.readValue(
                "{\"comment\":null}", RecordCommentUpdateRequest.class);

        assertThat(missingComment.hasCommentField()).isFalse();
        assertThat(nullComment.hasCommentField()).isTrue();
        assertThat(nullComment.getComment()).isNull();
        assertThat(validator.validate(nullComment)).isEmpty();
    }

    @Test
    void allowsEmptyCommentAndCommentWithAtMostEightyCharacters() throws Exception {
        RecordCommentUpdateRequest emptyComment = objectMapper.readValue(
                "{\"comment\":\"\"}", RecordCommentUpdateRequest.class);
        RecordCommentUpdateRequest maxLengthComment = objectMapper.readValue(
                "{\"comment\":\"" + "a".repeat(80) + "\"}",
                RecordCommentUpdateRequest.class);

        assertThat(emptyComment.getComment()).isEmpty();
        assertThat(validator.validate(emptyComment)).isEmpty();
        assertThat(validator.validate(maxLengthComment)).isEmpty();
    }

    @Test
    void rejectsCommentLongerThanEightyCharacters() throws Exception {
        RecordCommentUpdateRequest request = objectMapper.readValue(
                "{\"comment\":\"" + "a".repeat(81) + "\"}",
                RecordCommentUpdateRequest.class);

        Set<ConstraintViolation<RecordCommentUpdateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("comment")
                        && violation.getMessage().equals("COMMENT_TOO_LONG"));
    }
}
