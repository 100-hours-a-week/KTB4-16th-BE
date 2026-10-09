package com.ktb4.team16.mulo.user.validation;

import com.ktb4.team16.mulo.user.domain.PreferredGenre;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class PreferredGenresValidator
        implements ConstraintValidator<ValidPreferredGenres, List<String>> {
    @Override
    public boolean isValid(List<String> preferredGenres, ConstraintValidatorContext context) {
        // @NotNull reports null/missing input; an empty list is a valid explicit skip.
        return preferredGenres == null || PreferredGenre.isValidSelection(preferredGenres);
    }
}
