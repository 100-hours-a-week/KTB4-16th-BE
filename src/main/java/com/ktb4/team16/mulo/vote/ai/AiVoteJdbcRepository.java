package com.ktb4.team16.mulo.vote.ai;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

@Repository
@ConditionalOnProperty(prefix = "mulo.ai-db", name = "enabled", havingValue = "true")
public class AiVoteJdbcRepository implements AiVoteReadRepository {

    private final JdbcTemplate jdbcTemplate;
    private final HikariDataSource dataSource;

    public AiVoteJdbcRepository(AiDatabaseProperties properties) {
        Assert.hasText(properties.url(), "MULO_AI_DB_URL must be configured when AI DB integration is enabled");
        Assert.hasText(properties.username(), "MULO_AI_DB_USERNAME must be configured when AI DB integration is enabled");
        Assert.hasText(properties.password(), "MULO_AI_DB_PASSWORD must be configured when AI DB integration is enabled");
        this.dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(properties.url());
        dataSource.setUsername(properties.username());
        dataSource.setPassword(properties.password());
        dataSource.setReadOnly(true);
        dataSource.setMaximumPoolSize(2);
        dataSource.setPoolName("ai-vote-read-only");
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Map<Long, String> findAllUserGenres() {
        Map<Long, String> genres = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT user_id, music_genre FROM be_user_music_genres", resultSet -> {
            long userId = resultSet.getLong("user_id");
            String genre = resultSet.getString("music_genre");
            if (genre == null || genres.containsKey(userId)) {
                throw new IllegalStateException("AI 사용자 장르 뷰의 user_id 또는 music_genre 값이 올바르지 않습니다.");
            }
            genres.put(userId, genre);
        });
        return Map.copyOf(genres);
    }

    @Override
    public Optional<String> findUserGenre(Long userId) {
        return jdbcTemplate.query("""
                        SELECT music_genre
                        FROM be_user_music_genres
                        WHERE user_id = ?
                        """, resultSet -> resultSet.next()
                        ? Optional.ofNullable(resultSet.getString("music_genre")) : Optional.empty(), userId);
    }

    @Override
    public List<AiVoteQuestion> findQuestions(LocalDate fromDate, LocalDate toDate) {
        return jdbcTemplate.query("""
                        SELECT question_date, music_genre, question, option_a, option_b
                        FROM be_vote_questions
                        WHERE question_date >= ? AND question_date <= ?
                        """,
                (resultSet, rowNumber) -> new AiVoteQuestion(
                        resultSet.getDate("question_date").toLocalDate(),
                        resultSet.getString("music_genre"),
                        resultSet.getString("question"),
                        resultSet.getString("option_a"),
                        resultSet.getString("option_b")),
                fromDate, toDate);
    }

    @PreDestroy
    public void closeDataSource() {
        dataSource.close();
    }
}
