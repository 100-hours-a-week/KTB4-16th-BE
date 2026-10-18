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

    @Scheduled(cron = "0 45,50 23 * * *", zone = "Asia/Seoul")
    public void prefetchTomorrowQuestions() {
        prefetchQuestions(LocalDate.now(clock).plusDays(1));
    }

    public void prefetchQuestions(LocalDate targetDate) {
        if (snapshotStore.isReady(targetDate)) {
            return;
        }

        List<AiVoteQuestion> questions = readMissingQuestions(targetDate);
        List<VoteSnapshotStore.AssignmentRow> assignments = readMissingUserAssignments(targetDate);
        if (questions.isEmpty() && assignments.isEmpty()) {
            return;
        }
        prefetchWriter.save(targetDate, questions, assignments);
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

    private List<VoteSnapshotStore.AssignmentRow> readMissingUserAssignments(LocalDate targetDate) {
        List<User> users = userRepository.findAllByDeletedAtIsNull();
        Set<Long> savedUserIds = snapshotStore.findAssignments(targetDate).stream()
                .map(VoteSnapshotStore.AssignmentRow::userId)
                .collect(Collectors.toSet());
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
