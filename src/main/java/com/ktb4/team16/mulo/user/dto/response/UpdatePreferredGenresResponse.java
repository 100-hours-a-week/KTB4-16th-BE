package com.ktb4.team16.mulo.user.dto.response;

import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.message.UserMessage;
import java.util.List;

public record UpdatePreferredGenresResponse(String message, PreferredGenresData data) {
    public static UpdatePreferredGenresResponse from(User user) {
        List<String> preferredGenres = user.getPreferredGenres();
        return new UpdatePreferredGenresResponse(
                UserMessage.PREFERRED_GENRES_UPDATED.message(),
                new PreferredGenresData(
                        preferredGenres == null ? null : List.copyOf(preferredGenres),
                        user.isGenreOnboardingDone()));
    }

    public record PreferredGenresData(List<String> preferredGenres, boolean genreOnboardingDone) {
    }
}
