package com.ktb4.team16.mulo.vote.scheduler;

import com.ktb4.team16.mulo.user.domain.PreferredGenre;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.vote.ai.AiVoteQuestion;
import com.ktb4.team16.mulo.vote.ai.AiVoteReadRepository;
import com.ktb4.team16.mulo.vote.snapshot.VoteDailySnapshotWriter;
import com.ktb4.team16.mulo.vote.snapshot.VoteSnapshotStore;
import com.ktb4.team16.mulo.vote.snapshot.VoteUserGenreAssignment;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void snapshotToday() {
        createSnapshot(LocalDate.now(clock));
    }

    public void createSnapshot(LocalDate voteDate) {
        if (snapshotStore.isReady(voteDate)) {
            return;
        }
        if (!aiVoteReadRepository.isAvailable()) {
            log.warn("취향 투표 일일 스냅샷을 만들 수 없습니다: AI DB 연결이 설정되지 않았습니다. date={}", voteDate);
            return;
        }

        try {
            Map<Long, String> aiGenres = aiVoteReadRepository.findAllUserGenres();
            List<VoteUserGenreAssignment> assignments = findAssignments(
                    userRepository.findAllByDeletedAtIsNull(), aiGenres);
            List<AiVoteQuestion> selectedQuestions = selectQuestions(voteDate, assignments);
            snapshotWriter.write(voteDate, selectedQuestions, assignments);
        } catch (DataIntegrityViolationException exception) {
            if (snapshotStore.isReady(voteDate)) {
                return;
            }
            log.error("취향 투표 일일 스냅샷 저장에 실패했습니다. date={}, errorType={}",
                    voteDate, exception.getClass().getSimpleName());
        } catch (RuntimeException exception) {
            log.error("취향 투표 일일 스냅샷 준비에 실패했습니다. date={}, errorType={}",
                    voteDate, exception.getClass().getSimpleName());
        }
    }

    private List<AiVoteQuestion> selectQuestions(LocalDate voteDate,
            List<VoteUserGenreAssignment> assignments) {
        Set<String> requiredGenres = assignments.stream()
                .map(VoteUserGenreAssignment::musicGenre)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, AiVoteQuestion> selected = new LinkedHashMap<>();
        for (String genre : requiredGenres) {
            snapshotStore.findQuestionContent(voteDate, genre).ifPresent(existing ->
                    selected.put(genre, new AiVoteQuestion(voteDate, genre, existing.question(),
                            existing.optionA(), existing.optionB())));
        }

        Set<String> missingGenres = new LinkedHashSet<>(requiredGenres);
        missingGenres.removeAll(selected.keySet());
        if (missingGenres.isEmpty()) {
            return new ArrayList<>(selected.values());
        }

        Map<String, AiVoteQuestion> byDateAndGenre = new LinkedHashMap<>();
        for (AiVoteQuestion question : aiVoteReadRepository.findQuestions(
                voteDate.minusDays(1), voteDate)) {
            validateQuestion(question);
            String key = question.questionDate() + "|" + question.musicGenre();
            if (byDateAndGenre.putIfAbsent(key, question) != null) {
                throw new IllegalStateException("AI 질문 뷰에 날짜·그룹 중복 행이 있습니다.");
            }
        }
        for (String genre : missingGenres) {
            AiVoteQuestion question = byDateAndGenre.get(voteDate + "|" + genre);
            if (question == null) {
                question = byDateAndGenre.get(voteDate.minusDays(1) + "|" + genre);
            }
            if (question != null) {
                selected.put(genre, new AiVoteQuestion(voteDate, genre, question.question(),
                        question.optionA(), question.optionB()));
            }
        }
        return new ArrayList<>(selected.values());
    }

    private List<VoteUserGenreAssignment> findAssignments(List<User> users, Map<Long, String> aiGenres) {
        List<VoteUserGenreAssignment> assignments = new ArrayList<>();
        for (User user : users) {
            String genre = aiGenres.getOrDefault(user.getUserId(), COMMON_GENRE);
            validateGenre(genre);
            assignments.add(new VoteUserGenreAssignment(user.getUserId(), genre));
        }
        return assignments;
    }

    private void validateQuestion(AiVoteQuestion question) {
        validateGenre(question.musicGenre());
        if (question.question() == null || question.optionA() == null || question.optionB() == null) {
            throw new IllegalStateException("AI 질문 뷰에 질문 또는 선택지 값이 없습니다.");
        }
        if (question.question().length() > 100 || question.optionA().length() > 100
                || question.optionB().length() > 100) {
            throw new IllegalStateException("AI 질문 값이 Backend vote_questions 컬럼 길이를 초과합니다.");
        }
    }

    public static void validateGenre(String genre) {
        if (!ALLOWED_GENRES.contains(genre)) {
            throw new IllegalStateException("AI 사용자 장르 뷰에 허용되지 않은 music_genre 값이 있습니다.");
        }
    }
}
