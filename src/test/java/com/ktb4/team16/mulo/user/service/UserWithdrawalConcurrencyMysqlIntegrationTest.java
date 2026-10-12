package com.ktb4.team16.mulo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb4.team16.mulo.auth.exception.InvalidCredentialsException;
import com.ktb4.team16.mulo.auth.exception.InvalidRefreshTokenException;
import com.ktb4.team16.mulo.auth.service.AuthService;
import com.ktb4.team16.mulo.auth.service.LoginResult;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.exception.UserWithdrawalException;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 MySQL 사용자 행 잠금 아래 탈퇴와 인증 작업의 직렬화 결과를 검증한다. */
@SpringBootTest
class UserWithdrawalConcurrencyMysqlIntegrationTest {
    @Autowired UserWithdrawalService withdrawalService;
    @Autowired UserProfileService profileService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPlaceIds = new ArrayList<>();
    private final List<Long> createdTrackIds = new ArrayList<>();

    /** 테스트가 생성한 토큰과 사용자만 외래 키 순서대로 제거한다. */
    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM records WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId);
        }
        createdPlaceIds.forEach(id -> jdbcTemplate.update("DELETE FROM places WHERE place_id = ?", id));
        createdTrackIds.forEach(id -> jdbcTemplate.update(
                "DELETE FROM music_tracks WHERE music_track_id = ?", id));
    }

    /** 탈퇴 후 같은 DB 트랜잭션에서 실패하면 사용자와 기록의 변경이 함께 롤백된다. */
    @Test
    void transactionFailureRollsBackUserAndRecordSoftDelete() {
        User user = createUser();
        Long placeId = insert("INSERT INTO places (latitude, longitude) VALUES (37.5, 127.0)");
        createdPlaceIds.add(placeId);
        Long trackId = insert("""
                INSERT INTO music_tracks
                    (external_track_id, title, artist_name, album_image_url, external_url)
                VALUES (?, 'title', 'artist', 'album', 'external')
                """, UUID.randomUUID().toString().substring(0, 20));
        createdTrackIds.add(trackId);
        Long recordId = insert("""
                INSERT INTO records (user_id, place_id, music_track_id, mood_score)
                VALUES (?, ?, ?, 1)
                """, user.getUserId(), placeId, trackId);

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            withdrawalService.withdraw(user.getUserId(), "Correct123!");
            throw new IllegalStateException("forced failure after withdrawal changes");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM users WHERE user_id = ?", Object.class,
                user.getUserId())).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM records WHERE record_id = ?", Object.class,
                recordId)).isNull();
    }

    /** CT-01: 중복 탈퇴는 한 요청만 성공하고 다른 요청은 401 대상이 된다. */
    @Test
    void duplicateWithdrawalsHaveOneWinner() throws Exception {
        User user = createUser();
        List<Object> results = runTogether(
                () -> { withdrawalService.withdraw(user.getUserId(), "Correct123!"); return true; },
                () -> { withdrawalService.withdraw(user.getUserId(), "Correct123!"); return true; });

        assertThat(results).contains(true);
        assertThat(results).anyMatch(UnauthenticatedUserException.class::isInstance);
        assertThat(userRepository.existsByUserIdAndDeletedAtIsNull(user.getUserId())).isFalse();
    }

    /** CT-02·03: 로그인 경합 후에는 탈퇴 계정의 Refresh Token을 사용할 수 없다. */
    @RepeatedTest(5)
    void racingLoginCannotLeaveUsableSession() throws Exception {
        User user = createUser();
        List<Object> results = runTogether(
                () -> { withdrawalService.withdraw(user.getUserId(), "Correct123!"); return true; },
                () -> authService.login(user.getEmail(), "Correct123!"));

        assertThat(results).contains(true);
        assertThat(userRepository.existsByUserIdAndDeletedAtIsNull(user.getUserId())).isFalse();
        for (Object result : results) {
            if (result instanceof LoginResult login) {
                assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                        .isInstanceOf(InvalidRefreshTokenException.class);
            } else if (result != Boolean.TRUE) {
                assertThat(result).isInstanceOf(InvalidCredentialsException.class);
            }
        }
    }

    /** CT-04: 비밀번호 변경과 탈퇴는 한쪽만 성공하는 직렬 결과로 끝난다. */
    @Test
    void racingPasswordChangeAndWithdrawalHaveSerializedOutcome() throws Exception {
        User user = createUser();
        List<Object> results = runTogether(
                () -> { withdrawalService.withdraw(user.getUserId(), "Correct123!"); return true; },
                () -> profileService.updatePassword(user.getUserId(), "Correct123!", "Changed123!"));

        if (results.contains(true)) {
            assertThat(results).anyMatch(UnauthenticatedUserException.class::isInstance);
            assertThat(userRepository.existsByUserIdAndDeletedAtIsNull(user.getUserId())).isFalse();
        } else {
            assertThat(results).anyMatch(UserWithdrawalException.class::isInstance);
            assertThat(userRepository.existsByUserIdAndDeletedAtIsNull(user.getUserId())).isTrue();
            String hash = userRepository.findById(user.getUserId()).orElseThrow().getPasswordHash();
            assertThat(passwordEncoder.matches("Changed123!", hash)).isTrue();
        }
    }

    /** 서로 다른 커넥션에서 두 작업을 동시에 시작하고 결과 또는 예외를 수집한다. */
    private List<Object> runTogether(Callable<Object> first, Callable<Object> second)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> firstResult = executor.submit(() -> runAfterStart(start, first));
            Future<Object> secondResult = executor.submit(() -> runAfterStart(start, second));
            start.countDown();
            return List.of(firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    /** 시작 신호를 기다린 뒤 작업의 성공값 또는 RuntimeException을 반환한다. */
    private Object runAfterStart(CountDownLatch start, Callable<Object> operation)
            throws Exception {
        start.await();
        try {
            return operation.call();
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    /** 각 시나리오에 고유 이메일과 닉네임의 커밋된 사용자를 생성한다. */
    private User createUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User user = userRepository.saveAndFlush(User.signup(
                suffix + "@example.test", passwordEncoder.encode("Correct123!"),
                suffix.substring(0, 10)));
        createdUserIds.add(user.getUserId());
        return user;
    }

    /** 테스트 행을 삽입하고 생성된 기본 키를 반환한다. */
    private Long insert(String sql, Object... values) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
}
