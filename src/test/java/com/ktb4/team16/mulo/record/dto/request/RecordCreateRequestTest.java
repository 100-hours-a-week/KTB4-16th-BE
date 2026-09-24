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
                new RecordCreateRequest.Music("track-id"),
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
}
