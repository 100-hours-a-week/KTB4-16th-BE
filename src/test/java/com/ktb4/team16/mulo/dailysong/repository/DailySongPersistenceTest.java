package com.ktb4.team16.mulo.dailysong.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb4.team16.mulo.dailysong.entity.DailySongDispatchOutbox;
import com.ktb4.team16.mulo.dailysong.entity.DailySongDispatchOutboxStatus;
import com.ktb4.team16.mulo.dailysong.entity.DailySongJob;
import com.ktb4.team16.mulo.dailysong.entity.DailySongJobStatus;
import com.ktb4.team16.mulo.dailysong.entity.DailySongRun;
import com.ktb4.team16.mulo.dailysong.entity.DailySongRunStatus;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(DailySongPersistenceTest.SafeDatabaseCondition.class)
@SpringBootTest
@Transactional
class DailySongPersistenceTest {
    private static final LocalDate MYSQL_DATE_MIN = LocalDate.of(1000, 1, 1);
    private static final LocalDate MYSQL_DATE_MAX = LocalDate.of(9999, 12, 31);
    private static final long MAX_TEST_USER_ID = ChronoUnit.DAYS.between(MYSQL_DATE_MIN, MYSQL_DATE_MAX) + 1;

    @Autowired
    private DailySongRunRepository runs;

    @Autowired
    private DailySongJobRepository jobs;

    @Autowired
    private DailySongDispatchOutboxRepository outboxes;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsRunJobAndDispatchOutboxWithDatabaseDefaults() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = users.saveAndFlush(User.signup("daily-" + suffix + "@example.test", "test-hash", "ds" + suffix));
        assertThat(user.getUserId())
                .as("테스트 사용자 ID로 생성한 날짜가 MySQL DATE 범위에 있어야 합니다.")
                .isBetween(1L, MAX_TEST_USER_ID);
        LocalDate recommendationDate = MYSQL_DATE_MIN.plusDays(user.getUserId() - 1);
        DailySongRun run = runs.saveAndFlush(new DailySongRun(recommendationDate, recommendationDate.atTime(0, 30)));
        String notificationEventId = UUID.randomUUID().toString();
        DailySongJob job = jobs.saveAndFlush(new DailySongJob(recommendationDate, user.getUserId(), notificationEventId));
        String dispatchEventId = UUID.randomUUID().toString();
        outboxes.saveAndFlush(new DailySongDispatchOutbox(dispatchEventId, job.getJobId()));

        entityManager.clear();

        DailySongRun savedRun = runs.findById(recommendationDate).orElseThrow();
        DailySongJob savedJob = jobs.findByRecommendationDateAndUserId(recommendationDate, user.getUserId())
                .orElseThrow();
        DailySongDispatchOutbox savedOutbox = outboxes.findById(dispatchEventId).orElseThrow();

        assertThat(savedRun.getStatus()).isEqualTo(DailySongRunStatus.CREATING);
        assertThat(savedRun.getCreatedAt()).isNotNull();
        assertThat(savedJob.getStatus()).isEqualTo(DailySongJobStatus.PENDING);
        assertThat(savedJob.getRun().getRecommendationDate()).isEqualTo(recommendationDate);
        assertThat(savedJob.getUser().getUserId()).isEqualTo(user.getUserId());
        assertThat(savedJob.getNotificationEventId()).isEqualTo(notificationEventId);
        assertThat(savedJob.getCreatedAt()).isNotNull();
        assertThat(savedOutbox.getStatus()).isEqualTo(DailySongDispatchOutboxStatus.PENDING);
        assertThat(savedOutbox.getPublishAttemptCount()).isZero();
        assertThat(savedOutbox.getNextPublishAt()).isNotNull();
        assertThat(savedOutbox.getCreatedAt()).isNotNull();
        assertThat(savedOutbox.getJob().getJobId()).isEqualTo(job.getJobId());
    }

    public static class SafeDatabaseCondition implements ExecutionCondition {
        private static final String LOCAL_URL = "jdbc:mysql://127.0.0.1:3313/mulo_test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul";
        private static final String CI_URL = "jdbc:mysql://127.0.0.1:3307/mulo?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul";

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (System.getProperty("org.gradle.test.worker") == null) {
                return disabled();
            }

            boolean ci = "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
            String url = ci ? CI_URL : LOCAL_URL;
            String username = ci ? "mulo" : "mulo_test";
            String password = ci ? "mulo" : "mulo_test_only_password";

            if (!url.equals(System.getenv("DB_URL"))
                    || !username.equals(System.getenv("DB_USERNAME"))
                    || !password.equals(System.getenv("DB_PASSWORD"))
                    || !sameOrAbsent(System.getenv("SPRING_DATASOURCE_URL"), url)
                    || !sameOrAbsent(System.getenv("SPRING_DATASOURCE_USERNAME"), username)
                    || !sameOrAbsent(System.getenv("SPRING_DATASOURCE_PASSWORD"), password)
                    || !sameOrAbsent(System.getenv("SPRING_DATASOURCE_HIKARI_JDBC_URL"), url)
                    || !sameOrAbsent(System.getProperty("DB_URL"), url)
                    || !sameOrAbsent(System.getProperty("DB_USERNAME"), username)
                    || !sameOrAbsent(System.getProperty("DB_PASSWORD"), password)
                    || !sameOrAbsent(System.getProperty("SPRING_DATASOURCE_URL"), url)
                    || !sameOrAbsent(System.getProperty("SPRING_DATASOURCE_USERNAME"), username)
                    || !sameOrAbsent(System.getProperty("SPRING_DATASOURCE_PASSWORD"), password)
                    || !sameOrAbsent(System.getProperty("SPRING_DATASOURCE_HIKARI_JDBC_URL"), url)
                    || !sameOrAbsent(System.getProperty("spring.datasource.url"), url)
                    || !sameOrAbsent(System.getProperty("spring.datasource.username"), username)
                    || !sameOrAbsent(System.getProperty("spring.datasource.password"), password)
                    || !sameOrAbsent(System.getProperty("spring.datasource.hikari.jdbc-url"), url)
                    || System.getenv("SPRING_DATASOURCE_JNDI_NAME") != null
                    || System.getProperty("spring.datasource.jndi-name") != null
                    || System.getenv("SPRING_APPLICATION_JSON") != null
                    || System.getenv("SPRING_CONFIG_LOCATION") != null
                    || System.getenv("SPRING_CONFIG_ADDITIONAL_LOCATION") != null
                    || System.getenv("SPRING_CONFIG_IMPORT") != null
                    || System.getenv("SPRING_CONFIG_NAME") != null
                    || System.getProperty("spring.application.json") != null
                    || System.getProperty("spring.config.location") != null
                    || System.getProperty("spring.config.additional-location") != null
                    || System.getProperty("spring.config.import") != null
                    || System.getProperty("spring.config.name") != null
                    || !sameOrAbsent(System.getProperty("spring.profiles.active"), "test")
                    || (ci
                    ? !sameOrAbsent(System.getenv("SPRING_PROFILES_ACTIVE"), "test")
                    : !"test".equals(System.getenv("SPRING_PROFILES_ACTIVE")))) {
                return disabled();
            }

            return ConditionEvaluationResult.enabled("격리된 테스트 DB 환경이 확인되었습니다.");
        }

        private static boolean sameOrAbsent(String value, String expected) {
            return value == null || expected.equals(value);
        }

        private static ConditionEvaluationResult disabled() {
            return ConditionEvaluationResult.disabled("격리된 테스트 DB 환경을 확인할 수 없어 테스트를 실행하지 않습니다.");
        }
    }
}
