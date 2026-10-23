package com.ktb4.team16.mulo.vote.scheduler;

import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import com.ktb4.team16.mulo.vote.ai.AiVoteReadRepository;
import com.ktb4.team16.mulo.vote.snapshot.VoteQuestionPrefetchWriter;
import com.ktb4.team16.mulo.vote.snapshot.VoteSnapshotStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoteQuestionPrefetchService {
    private static final Logger log = LoggerFactory.getLogger(VoteQuestionPrefetchService.class);
    private static final String COMMON_GENRE = "공통";
    private static final Set<String> ALLOWED_GENRES = Stream.concat(
                    java.util.Arrays.stream(com.ktb4.team16.mulo.user.domain.PreferredGenre.values())
                            .map(com.ktb4.team16.mulo.user.domain.PreferredGenre::value),
                    Stream.of(COMMON_GENRE))
            .collect(Collectors.toUnmodifiableSet());

    private final AiVoteReadRepository aiVoteReadRepository;
    private final UserRepository userRepository;
    private final VoteSnapshotStore snapshotStore;
    private final VoteQuestionPrefetchWriter prefetchWriter;
    private final Clock clock;

    @Scheduled(cron = "0 45 23 * * *", zone = "Asia/Seoul")
    public void prefetchTomorrowQuestions() {
        prefetchQuestions(LocalDate.now(clock).plusDays(1), false);
    }

    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void retryTomorrowQuestions() {
        prefetchQuestions(LocalDate.now(clock).plusDays(1), true);
    }

    public void prefetchQuestions(LocalDate targetDate) {
        prefetchQuestions(targetDate, false);
    }

    private void prefetchQuestions(LocalDate targetDate, boolean retry) {
        if (snapshotStore.isReady(targetDate)) {
            log.info("취향 투표 사전 적재를 생략했습니다. date={}, reason=SNAPSHOT_READY, retry={}",
                    targetDate, retry);
            return;
        }

        List<User> users = userRepository.findAllByDeletedAtIsNull();
        Set<Long> savedUserIds = snapshotStore.findAssignments(targetDate).stream()
                .map(VoteSnapshotStore.AssignmentRow::userId)
                .collect(Collectors.toSet());
        Set<String> existingGenres = findExistingGenres(targetDate);
        long missingUsers = users.stream()
                .map(User::getUserId)
                .filter(userId -> !savedUserIds.contains(userId))
                .count();
        Set<String> missingGenres = new HashSet<>(ALLOWED_GENRES);
        missingGenres.removeAll(existingGenres);
        if (missingUsers == 0 && missingGenres.isEmpty()) {
            log.info("취향 투표 사전 적재를 생략했습니다. date={}, reason=ALL_DATA_ALREADY_STORED, retry={}",
                    targetDate, retry);
            return;
        }

        List<AiVoteQuestion> questions = readMissingQuestions(targetDate);
        List<VoteSnapshotStore.AssignmentRow> assignments = readMissingUserAssignments(
                targetDate, users, savedUserIds);
        if (questions.isEmpty() && assignments.isEmpty()) {
            if (snapshotStore.findQuestionContent(targetDate, COMMON_GENRE).isPresent()) {
                log.info("새 사전 적재 데이터는 없지만 당일 공통 질문 fallback을 사용할 수 있습니다. "
                                + "date={}, retry={}, missingQuestionGroups={}, missingUsers={}, outcome=PARTIAL",
                        targetDate, retry, missingGenres.size(), missingUsers);
                return;
            }
            log.error("취향 투표 사전 적재가 완료되지 않았습니다. date={}, retry={}, "
                            + "missingQuestionGroups={}, missingUsers={}, outcome=FAILED",
                    targetDate, retry, missingGenres.size(), missingUsers);
            return;
        }
        prefetchWriter.save(targetDate, questions, assignments);
        boolean complete = isPrefetchComplete(targetDate);
        log.info("취향 투표 사전 적재가 종료되었습니다. date={}, retry={}, outcome={}, questionGroupsSaved={}, "
                        + "userAssignmentsSaved={}, missingQuestionGroupsBefore={}, missingUsersBefore={}",
                targetDate, retry, complete ? "SUCCESS" : "PARTIAL", questions.size(), assignments.size(),
                missingGenres.size(), missingUsers);
    }

    private List<AiVoteQuestion> readMissingQuestions(LocalDate targetDate) {
        Set<String> missingGenres = new HashSet<>(ALLOWED_GENRES);
        missingGenres.removeAll(findExistingGenres(targetDate));
        if (missingGenres.isEmpty() || !isAiAvailable(targetDate, "질문")) {
            return List.of();
        }
        try {
            return validateAndSelect(targetDate,
                    aiVoteReadRepository.findQuestions(targetDate, targetDate), missingGenres);
        } catch (RuntimeException exception) {
            log.error("다음 날 질문 사전 조회에 실패했습니다. date={}, errorType={}",
                    targetDate, exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<VoteSnapshotStore.AssignmentRow> readMissingUserAssignments(LocalDate targetDate,
            List<User> users, Set<Long> savedUserIds) {
        List<User> missingUsers = users.stream()
                .filter(user -> !savedUserIds.contains(user.getUserId()))
                .toList();
        if (missingUsers.isEmpty() || !isAiAvailable(targetDate, "사용자 장르")) {
            return List.of();
        }
        try {
            Map<Long, String> aiGenres = aiVoteReadRepository.findAllUserGenres();
            List<VoteSnapshotStore.AssignmentRow> assignments = new ArrayList<>();
            for (User user : missingUsers) {
                String genre = aiGenres.getOrDefault(user.getUserId(), COMMON_GENRE);
                VoteDailySnapshotService.validateGenre(genre);
                assignments.add(new VoteSnapshotStore.AssignmentRow(user.getUserId(), genre, null));
            }
            return assignments;
        } catch (RuntimeException exception) {
            log.error("다음 날 사용자 장르 사전 조회에 실패했습니다. date={}, errorType={}",
                    targetDate, exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private boolean isAiAvailable(LocalDate targetDate, String dataType) {
        try {
            if (aiVoteReadRepository.isAvailable()) {
                return true;
            }
            log.warn("다음 날 취향 투표 {} 사전 조회를 사용할 수 없습니다. date={}", dataType, targetDate);
        } catch (RuntimeException exception) {
            log.error("다음 날 취향 투표 {} 사전 조회 가능 여부를 확인하지 못했습니다. date={}, errorType={}",
                    dataType, targetDate, exception.getClass().getSimpleName());
        }
        return false;
    }

    private Set<String> findExistingGenres(LocalDate targetDate) {
        return ALLOWED_GENRES.stream()
                .filter(genre -> snapshotStore.findQuestionContent(targetDate, genre).isPresent())
                .collect(Collectors.toSet());
    }

    private boolean isPrefetchComplete(LocalDate targetDate) {
        Set<String> existingGenres = findExistingGenres(targetDate);
        if (!existingGenres.containsAll(ALLOWED_GENRES)) {
            return false;
        }
        Set<Long> savedUserIds = snapshotStore.findAssignments(targetDate).stream()
                .map(VoteSnapshotStore.AssignmentRow::userId)
                .collect(Collectors.toSet());
        return userRepository.findAllByDeletedAtIsNull().stream()
                .map(User::getUserId)
                .allMatch(savedUserIds::contains);
    }

    private List<AiVoteQuestion> validateAndSelect(LocalDate targetDate,
            List<AiVoteQuestion> questions, Set<String> missingGenres) {
        Set<String> seenGenres = new HashSet<>();
        List<AiVoteQuestion> selected = new ArrayList<>();
        for (AiVoteQuestion question : questions) {
            VoteDailySnapshotService.validateGenre(question.musicGenre());
            if (!targetDate.equals(question.questionDate())) {
                throw new IllegalStateException("AI 질문 뷰에 요청 날짜와 다른 질문이 있습니다.");
            }
            if (question.question() == null || question.optionA() == null || question.optionB() == null) {
                throw new IllegalStateException("AI 질문 뷰에 질문 또는 선택지 값이 없습니다.");
            }
            if (question.question().length() > 100 || question.optionA().length() > 100
                    || question.optionB().length() > 100) {
                throw new IllegalStateException("AI 질문 값이 Backend vote_questions 컬럼 길이를 초과합니다.");
            }
            if (!seenGenres.add(question.musicGenre())) {
                throw new IllegalStateException("AI 질문 뷰에 날짜·그룹 중복 행이 있습니다.");
            }
            if (missingGenres.contains(question.musicGenre())) {
                selected.add(question);
            }
        }
        return selected;
    }
}
