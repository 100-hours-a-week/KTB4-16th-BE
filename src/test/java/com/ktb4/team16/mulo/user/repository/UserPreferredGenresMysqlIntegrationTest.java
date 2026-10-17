package com.ktb4.team16.mulo.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.user.dto.response.UserProfileResponse;
import com.ktb4.team16.mulo.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserPreferredGenresMysqlIntegrationTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void preferredGenresJsonRoundTripsAndLegacyCompletionMismatchDoesNotCompleteOnboarding() {
        User unanswered = saveUser();
        Long unansweredId = unanswered.getUserId();

        User selected = saveUser();
        selected.updatePreferredGenres(List.of("인디음악", "재즈", "J-POP"));
        entityManager.flush();
        Long selectedId = selected.getUserId();

        User singleSelection = saveUser();
        singleSelection.updatePreferredGenres(List.of("발라드"));
        entityManager.flush();
        Long singleSelectionId = singleSelection.getUserId();

        User legacyNull = saveUser();
        ReflectionTestUtils.setField(legacyNull, "genreOnboardingDone", true);
        User legacyEmpty = saveUser();
        ReflectionTestUtils.setField(legacyEmpty, "preferredGenres", List.of());
        ReflectionTestUtils.setField(legacyEmpty, "genreOnboardingDone", true);
        entityManager.flush();
        Long legacyNullId = legacyNull.getUserId();
        Long legacyEmptyId = legacyEmpty.getUserId();

        entityManager.clear();

        User reloadedUnanswered = userRepository.findById(unansweredId).orElseThrow();
        assertThat(reloadedUnanswered.getPreferredGenres()).isNull();
        assertThat(reloadedUnanswered.isGenreOnboardingDone()).isFalse();
        assertThat(reloadedUnanswered.getCreatedAt()).isNotNull();
        assertThat(UserProfileResponse.from(reloadedUnanswered).data().createdAt())
                .isEqualTo(reloadedUnanswered.getCreatedAt());

        User reloadedSelected = userRepository.findById(selectedId).orElseThrow();
        assertThat(reloadedSelected.getPreferredGenres())
                .containsExactly("인디음악", "재즈", "J-POP");
        assertThat(reloadedSelected.isGenreOnboardingDone()).isTrue();
        assertThat(UserProfileResponse.from(reloadedSelected).data().genreOnboardingDone())
                .isTrue();

        User reloadedSingleSelection = userRepository.findById(singleSelectionId).orElseThrow();
        assertThat(reloadedSingleSelection.getPreferredGenres()).containsExactly("발라드");
        assertThat(reloadedSingleSelection.isGenreOnboardingDone()).isTrue();
        assertThat(UserProfileResponse.from(reloadedSingleSelection).data().genreOnboardingDone())
                .isTrue();

        User reloadedLegacyNull = userRepository.findById(legacyNullId).orElseThrow();
        assertThat(reloadedLegacyNull.getPreferredGenres()).isNull();
        assertThat(reloadedLegacyNull.isGenreOnboardingDone()).isTrue();
        assertThat(UserProfileResponse.from(reloadedLegacyNull).data().genreOnboardingDone())
                .isFalse();

        User reloadedLegacyEmpty = userRepository.findById(legacyEmptyId).orElseThrow();
        assertThat(reloadedLegacyEmpty.getPreferredGenres()).isEmpty();
        assertThat(reloadedLegacyEmpty.isGenreOnboardingDone()).isTrue();
        assertThat(UserProfileResponse.from(reloadedLegacyEmpty).data().genreOnboardingDone())
                .isFalse();
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User user = User.signup(
                suffix + "@example.test", "password-hash", suffix.substring(0, 10));
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }
}
