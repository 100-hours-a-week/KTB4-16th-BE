package com.ktb4.team16.mulo.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserPreferredGenresMysqlIntegrationTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void preferredGenresJsonRoundTripsAndSkipStoresSqlNull() {
        User unanswered = saveUser();
        Long unansweredId = unanswered.getUserId();

        User selected = saveUser();
        selected.updatePreferredGenres(List.of("인디음악", "재즈", "J-POP"));
        entityManager.flush();
        Long selectedId = selected.getUserId();

        User skipped = saveUser();
        skipped.updatePreferredGenres(List.of());
        entityManager.flush();
        Long skippedId = skipped.getUserId();

        entityManager.clear();

        User reloadedUnanswered = userRepository.findById(unansweredId).orElseThrow();
        assertThat(reloadedUnanswered.getPreferredGenres()).isNull();
        assertThat(reloadedUnanswered.isGenreOnboardingDone()).isFalse();

        User reloadedSelected = userRepository.findById(selectedId).orElseThrow();
        assertThat(reloadedSelected.getPreferredGenres())
                .containsExactly("인디음악", "재즈", "J-POP");
        assertThat(reloadedSelected.isGenreOnboardingDone()).isTrue();

        User reloadedSkipped = userRepository.findById(skippedId).orElseThrow();
        assertThat(reloadedSkipped.getPreferredGenres()).isNull();
        assertThat(reloadedSkipped.isGenreOnboardingDone()).isTrue();
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
