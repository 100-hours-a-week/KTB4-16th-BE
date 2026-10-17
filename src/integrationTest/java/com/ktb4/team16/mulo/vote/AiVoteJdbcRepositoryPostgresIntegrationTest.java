package com.ktb4.team16.mulo.vote;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.vote.ai.AiDatabaseProperties;
import com.ktb4.team16.mulo.vote.ai.AiVoteJdbcRepository;
import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(
        named = "MULO_AI_POSTGRES_TEST_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/mulo_ai_contract_test.*")
class AiVoteJdbcRepositoryPostgresIntegrationTest {
    private static final long FIXTURE_USER_ID = 987_654_321L;
    private static final LocalDate QUESTION_DATE = LocalDate.of(2026, 10, 17);

    @Test
    void readsContractViewsAndMapsQuestionDate() throws Exception {
        String url = System.getenv("MULO_AI_POSTGRES_TEST_URL");
        String username = System.getenv("MULO_AI_POSTGRES_TEST_USERNAME");
        String password = System.getenv("MULO_AI_POSTGRES_TEST_PASSWORD");
        prepareFixture(url, username, password);

        AiVoteJdbcRepository repository = new AiVoteJdbcRepository(
                new AiDatabaseProperties(true, url, username, password));
        try {
            assertThat(repository.isAvailable()).isTrue();
            assertThat(repository.findAllUserGenres()).containsEntry(FIXTURE_USER_ID, "공통");
            assertThat(repository.findUserGenre(FIXTURE_USER_ID)).contains("공통");
            assertThat(repository.findQuestions(QUESTION_DATE, QUESTION_DATE))
                    .containsExactly(new AiVoteQuestion(
                            QUESTION_DATE, "공통", "PostgreSQL fixture 질문", "Fixture A", "Fixture B"));
        } finally {
            repository.closeDataSource();
        }
    }

    private void prepareFixture(String url, String username, String password) throws Exception {
        try (var connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS fixture_user_music_genres "
                    + "(user_id BIGINT PRIMARY KEY, music_genre VARCHAR(50), updated_at TIMESTAMPTZ)");
            statement.execute("CREATE TABLE IF NOT EXISTS fixture_vote_questions "
                    + "(question_date DATE, music_genre VARCHAR(50), question VARCHAR(100), "
                    + "option_a VARCHAR(100), option_b VARCHAR(100), PRIMARY KEY (question_date, music_genre))");
            statement.execute("CREATE OR REPLACE VIEW be_user_music_genres AS "
                    + "SELECT user_id, music_genre, updated_at FROM fixture_user_music_genres");
            statement.execute("CREATE OR REPLACE VIEW be_vote_questions AS "
                    + "SELECT question_date, music_genre, question, option_a, option_b "
                    + "FROM fixture_vote_questions");
            statement.execute("INSERT INTO fixture_user_music_genres (user_id, music_genre, updated_at) "
                    + "VALUES (" + FIXTURE_USER_ID + ", '공통', CURRENT_TIMESTAMP) "
                    + "ON CONFLICT (user_id) DO UPDATE SET music_genre = EXCLUDED.music_genre, "
                    + "updated_at = EXCLUDED.updated_at");
            statement.execute("INSERT INTO fixture_vote_questions "
                    + "(question_date, music_genre, question, option_a, option_b) VALUES "
                    + "('" + QUESTION_DATE + "', '공통', 'PostgreSQL fixture 질문', 'Fixture A', 'Fixture B') "
                    + "ON CONFLICT (question_date, music_genre) DO UPDATE SET "
                    + "question = EXCLUDED.question, option_a = EXCLUDED.option_a, option_b = EXCLUDED.option_b");
        }
    }
}
