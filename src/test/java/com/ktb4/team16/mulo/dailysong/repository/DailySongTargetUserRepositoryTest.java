package com.ktb4.team16.mulo.dailysong.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(DailySongPersistenceTest.SafeDatabaseCondition.class)
@SpringBootTest
@Transactional
class DailySongTargetUserRepositoryTest {
    @Autowired private DailySongTargetUserRepository targets;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void selectsCutoffUsersInIdOrderAcrossPages() {
        LocalDateTime cutoff = LocalDateTime.of(1500, 1, 1, 0, 30);
        long before = createUser(cutoff.minusSeconds(1), null);
        long at = createUser(cutoff, null);
        createUser(cutoff.plusSeconds(1), null);
        long withdrawnAfter = createUser(cutoff.minusSeconds(1), cutoff.plusSeconds(1));
        createUser(cutoff.minusSeconds(1), cutoff);

        List<Long> firstPage = targets.findUserIdsAtCutoff(cutoff, before - 1, 2);
        List<Long> secondPage = targets.findUserIdsAtCutoff(cutoff, firstPage.getLast(), 2);

        assertThat(firstPage).containsExactly(before, at);
        assertThat(secondPage).containsExactly(withdrawnAfter);
    }

    @Test
    void rejectsNonPositivePageSize() {
        assertThatThrownBy(() -> targets.findUserIdsAtCutoff(LocalDateTime.now(), 0, 0))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("오늘의 노래 대상 조회 크기는 1 이상이어야 합니다.");
    }

    private long createUser(LocalDateTime createdAt, LocalDateTime deletedAt) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = users.saveAndFlush(User.signup("daily-" + suffix + "@example.test", "test-hash", "ds" + suffix));
        jdbcTemplate.update("UPDATE users SET created_at = ?, deleted_at = ? WHERE user_id = ?",
                createdAt, deletedAt, user.getUserId());
        return user.getUserId();
    }
}
