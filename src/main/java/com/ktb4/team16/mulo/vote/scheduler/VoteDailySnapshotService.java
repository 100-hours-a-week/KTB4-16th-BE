package com.ktb4.team16.mulo.vote.scheduler;

import com.ktb4.team16.mulo.user.domain.PreferredGenre;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import com.ktb4.team16.mulo.vote.ai.AiVoteReadRepository;
import com.ktb4.team16.mulo.vote.snapshot.VoteDailySnapshotWriter;
import com.ktb4.team16.mulo.vote.snapshot.VoteSnapshotStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoteDailySnapshotService {
    private static final Logger log = LoggerFactory.getLogger(VoteDailySnapshotService.class);
    private static final String COMMON_GENRE = "공통";
    private static final Set<String> ALLOWED_GENRES = java.util.stream.Stream.concat(
                    java.util.Arrays.stream(PreferredGenre.values()).map(PreferredGenre::value),
                    java.util.stream.Stream.of(COMMON_GENRE))
            .collect(Collectors.toUnmodifiableSet());

    private final AiVoteReadRepository aiVoteReadRepository;
    private final UserRepository userRepository;
    private final VoteSnapshotStore snapshotStore;
    private final VoteDailySnapshotWriter snapshotWriter;
    private final Clock clock;

    @Scheduled(cron = "0 55 23 * * *", zone = "Asia/Seoul")
    public void finalizeTomorrowSnapshot() {
        createSnapshot(LocalDate.now(clock).plusDays(1));
    }

    public void createSnapshot(LocalDate voteDate) {
        if (snapshotStore.isReady(voteDate)) {
            return;
        }

        long startedAt = System.nanoTime();
        String outcome = "READY";
        try {
            List<Long> activeUserIds = userRepository.findAllByDeletedAtIsNull().stream()
                    .map(User::getUserId)
                    .toList();
            Optional<Map<Long, String>> userGenres = readUserGenres(voteDate);
            List<AiVoteQuestion> questions = readQuestions(voteDate);
            snapshotWriter.finalizeSnapshot(voteDate, questions, activeUserIds, userGenres);
        } catch (DataIntegrityViolationException exception) {
            if (!snapshotStore.isReady(voteDate)) {
                outcome = "FAILED";
                log.error("취향 투표 스냅샷 확정에 실패했습니다. date={}, errorType={}",
                        voteDate, exception.getClass().getSimpleName());
            }
        } catch (RuntimeException exception) {
            outcome = "FAILED";
            log.error("취향 투표 스냅샷 확정에 실패했습니다. date={}, errorType={}",
                    voteDate, exception.getClass().getSimpleName());
        } finally {
            log.info("취향 투표 스냅샷 확정 작업 종료. date={}, outcome={}, elapsedMs={}",
                    voteDate, outcome, (System.nanoTime() - startedAt) / 1_000_000);
        }
    }

    private Optional<Map<Long, String>> readUserGenres(LocalDate voteDate) {
        if (!isAiAvailable(voteDate, "사용자 장르")) {
            log.warn("AI 사용자 장르 조회를 사용할 수 없습니다. date={}", voteDate);
            return Optional.empty();
        }
        try {
            return Optional.of(Map.copyOf(aiVoteReadRepository.findAllUserGenres()));
        } catch (RuntimeException exception) {
            log.error("AI 사용자 장르 조회에 실패했습니다. date={}, errorType={}",
                    voteDate, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private List<AiVoteQuestion> readQuestions(LocalDate voteDate) {
        if (!isAiAvailable(voteDate, "질문")) {
            log.warn("AI 질문 조회를 사용할 수 없습니다. date={}", voteDate);
            return List.of();
        }
        try {
            List<AiVoteQuestion> questions = aiVoteReadRepository.findQuestions(voteDate, voteDate);
            validateQuestions(voteDate, questions);
            return questions;
        } catch (RuntimeException exception) {
            log.error("AI 질문 조회에 실패했습니다. date={}, errorType={}",
                    voteDate, exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private boolean isAiAvailable(LocalDate voteDate, String dataType) {
        try {
            return aiVoteReadRepository.isAvailable();
        } catch (RuntimeException exception) {
            log.error("AI {} 조회 가능 여부를 확인하지 못했습니다. date={}, errorType={}",
                    dataType, voteDate, exception.getClass().getSimpleName());
            return false;
        }
    }

    private void validateQuestions(LocalDate voteDate, List<AiVoteQuestion> questions) {
        Set<String> seenGenres = new HashSet<>();
        for (AiVoteQuestion question : questions) {
            validateGenre(question.musicGenre());
            if (!voteDate.equals(question.questionDate())) {
                throw new IllegalStateException("AI 질문 뷰에 요청 날짜와 다른 질문이 있습니다.");
            }
            if (question.question() == null || question.optionA() == null || question.optionB() == null
                    || question.question().length() > 100 || question.optionA().length() > 100
                    || question.optionB().length() > 100) {
                throw new IllegalStateException("AI 질문 값이 Backend vote_questions 컬럼 조건과 맞지 않습니다.");
            }
            if (!seenGenres.add(question.musicGenre())) {
                throw new IllegalStateException("AI 질문 뷰에 날짜·그룹 중복 행이 있습니다.");
            }
        }
    }

    public static void validateGenre(String genre) {
        if (!ALLOWED_GENRES.contains(genre)) {
            throw new IllegalStateException("AI 사용자 장르 뷰에 허용되지 않은 music_genre 값이 있습니다.");
        }
    }
}
