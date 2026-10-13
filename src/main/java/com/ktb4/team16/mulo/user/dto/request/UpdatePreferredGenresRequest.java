package com.ktb4.team16.mulo.user.dto.request;

import com.ktb4.team16.mulo.user.validation.ValidPreferredGenres;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdatePreferredGenresRequest(
        @NotNull(message = "INVALID_INPUT_VALUE")
        @ValidPreferredGenres
        List<String> preferredGenres
) { }
