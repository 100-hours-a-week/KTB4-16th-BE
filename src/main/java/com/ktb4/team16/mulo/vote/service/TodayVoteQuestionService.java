package com.ktb4.team16.mulo.vote.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.global.exception.UnauthenticatedUserException;
import com.ktb4.team16.mulo.match.repository.VoteAnswerRepository;
import com.ktb4.team16.mulo.match.repository.VoteQuestionRepository;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import com.ktb4.team16.mulo.vote.ai.AiVoteReadRepository;
import com.ktb4.team16.mulo.vote.dto.response.TodayVoteQuestionResponse;
import com.ktb4.team16.mulo.vote.entity.VoteAnswer;
import com.ktb4.team16.mulo.vote.entity.VoteQuestion;
import com.ktb4.team16.mulo.vote.exception.VoteDomainException;
import com.ktb4.team16.mulo.vote.scheduler.VoteDailySnapshotService;
import com.ktb4.team16.mulo.vote.snapshot.VoteDailyAssignment;
import com.ktb4.team16.mulo.vote.snapshot.VoteDailyAssignmentWriter;
import com.ktb4.team16.mulo.vote.snapshot.VoteSnapshotStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TodayVoteQuestionService {
    private static final String COMMON_GENRE = "공통";

    private final UserRepository userRepository;
    private final VoteSnapshotStore snapshotStore;
    private final VoteDailyAssignmentWriter assignmentWriter;
    private final AiVoteReadRepository aiVoteReadRepository;
    private final VoteQuestionRepository voteQuestionRepository;
    private final VoteAnswerRepository voteAnswerRepository;
    private final VoteAnswerResultService voteAnswerResultService;
    private final Clock clock;

    public TodayVoteQuestionResponse getToday(Long userId) {
        VoteQuestion question = resolveTodayQuestion(userId);
        Optional<VoteAnswer> answer = voteAnswerRepository.findByVoteQuestion_VoteQuestionIdAndUser_UserId(
                question.getVoteQuestionId(), userId);
        var result = answer.map(ignored -> voteAnswerResultService.getResult(question)).orElse(null);
        return new TodayVoteQuestionResponse("투표 조회 성공",
                new TodayVoteQuestionResponse.Data(question.getVoteQuestionId(),
                        question.getQuestionText(), question.getOptionA(), question.getOptionB(),
                        answer.map(VoteAnswer::getSelectedOption).orElse(null),
                        answer.map(VoteAnswer::getVoteAnswerId).orElse(null), result));
    }

    public VoteQuestion resolveTodayQuestion(Long userId) {
        LocalDate today = LocalDate.now(clock);
        userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(UnauthenticatedUserException::new);
        if (!snapshotStore.isReady(today)) {
            throw new VoteDomainException(ErrorCode.VOTE_SNAPSHOT_NOT_READY);
        }

        VoteDailyAssignment assignment = snapshotStore.findAssignment(today, userId)
                .orElseGet(() -> assignLateUser(today, userId));
        if (assignment.voteQuestionId() == null) {
            throw new VoteDomainException(ErrorCode.TODAY_VOTE_NOT_FOUND);
        }
        VoteQuestion question = voteQuestionRepository.findById(assignment.voteQuestionId())
                .orElseThrow(() -> new VoteDomainException(ErrorCode.TODAY_VOTE_NOT_FOUND));
        if (!today.equals(question.getVoteDate())
                || (!assignment.musicGenre().equals(question.getMusicGenre())
                && !COMMON_GENRE.equals(question.getMusicGenre()))) {
            throw new VoteDomainException(ErrorCode.VOTE_SNAPSHOT_NOT_READY);
        }
        return question;
    }

    private VoteDailyAssignment assignLateUser(LocalDate today, Long userId) {
        if (!aiVoteReadRepository.isAvailable()) {
            throw new VoteDomainException(ErrorCode.VOTE_SNAPSHOT_NOT_READY);
        }
        try {
            String genre = aiVoteReadRepository.findUserGenre(userId).orElse(COMMON_GENRE);
            VoteDailySnapshotService.validateGenre(genre);
            Long questionId = snapshotStore.findQuestionId(today, genre).orElse(null);
            if (questionId == null && !COMMON_GENRE.equals(genre)) {
                questionId = snapshotStore.findQuestionId(today, COMMON_GENRE).orElse(null);
            }
            if (questionId == null) {
                throw new VoteDomainException(ErrorCode.TODAY_VOTE_NOT_FOUND);
            }
            VoteDailyAssignment assignment = assignmentWriter.assignIfMissing(
                    today, userId, genre, questionId);
            if (assignment == null) {
                throw new VoteDomainException(ErrorCode.VOTE_SNAPSHOT_NOT_READY);
            }
            return assignment;
        } catch (DataAccessException | IllegalStateException exception) {
            throw new VoteDomainException(ErrorCode.VOTE_SNAPSHOT_NOT_READY);
        }
    }
}
