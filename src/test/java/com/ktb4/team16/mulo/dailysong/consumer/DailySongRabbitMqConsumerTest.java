package com.ktb4.team16.mulo.dailysong.consumer;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.dailysong.entity.DailySongJobStatus;
import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository;
import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository.Claim;
import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository.JobState;
import com.ktb4.team16.mulo.dailysong.service.DailySongJobProcessingService;
import com.ktb4.team16.mulo.dailysong.service.DailySongJobProcessingService.ProcessingOutcome;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;

class DailySongRabbitMqConsumerTest {
    private final DailySongJobClaimRepository claims = mock(DailySongJobClaimRepository.class);
    private final DailySongJobProcessingService processing = mock(DailySongJobProcessingService.class);
    private final Channel channel = mock(Channel.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.ofHours(9));
    private final DailySongRabbitMqConsumer consumer = new DailySongRabbitMqConsumer(
            JsonMapper.builder().build(), claims, processing, clock);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 10, 9, 0);

    @BeforeEach
    void configure() {
        when(claims.claim(123, now, now.plusMinutes(5))).thenReturn(Optional.empty());
    }

    @Test
    void acknowledgesOnlyAfterProcessingReturns() throws Exception {
        Claim claim = new Claim(123, 13, LocalDate.of(2026, 11, 10), now, "claim-token");
        when(claims.claim(123, now, now.plusMinutes(5))).thenReturn(Optional.of(claim));
        when(processing.process(claim)).thenReturn(ProcessingOutcome.COMPLETED);
        when(claims.findState(123)).thenReturn(Optional.of(state(DailySongJobStatus.COMPLETED, null, null)));

        consumer.receive(message("{\"jobId\":123}"), channel);

        var order = inOrder(processing, claims, channel);
        order.verify(processing).process(claim);
        order.verify(claims).findState(123);
        order.verify(channel).basicAck(7, false);
    }

    @Test
    void acknowledgesAiFailureOnlyAfterFailedStateIsConfirmed() throws Exception {
        Claim claim = new Claim(123, 13, LocalDate.of(2026, 11, 10), now, "claim-token");
        when(claims.claim(123, now, now.plusMinutes(5))).thenReturn(Optional.of(claim));
        when(processing.process(claim)).thenReturn(ProcessingOutcome.FAILURE_RECORDED);
        when(claims.findState(123)).thenReturn(Optional.of(state(DailySongJobStatus.FAILED, null, null)));

        consumer.receive(message("{\"jobId\":123}"), channel);

        var order = inOrder(processing, claims, channel);
        order.verify(processing).process(claim);
        order.verify(claims).findState(123);
        order.verify(channel).basicAck(7, false);
    }

    @Test
    void unconfirmedCompletionUsesLeaseRecoveryWhenClaimIsStillProcessing() throws Exception {
        Claim claim = new Claim(123, 13, LocalDate.of(2026, 11, 10), now, "claim-token");
        when(claims.claim(123, now, now.plusMinutes(5))).thenReturn(Optional.of(claim));
        when(processing.process(claim)).thenReturn(ProcessingOutcome.NOT_APPLIED);
        when(claims.findState(123)).thenReturn(Optional.of(
                state(DailySongJobStatus.PROCESSING, claim.token(), now.plusMinutes(5))));

        consumer.receive(message("{\"jobId\":123}"), channel);

        verify(claims).recoverExpired(123, now);
        verify(channel).basicAck(7, false);
    }

    @Test
    void unconfirmedFailureAcknowledgesOnlyConfirmedTerminalState() throws Exception {
        Claim claim = new Claim(123, 13, LocalDate.of(2026, 11, 10), now, "claim-token");
        when(claims.claim(123, now, now.plusMinutes(5))).thenReturn(Optional.of(claim));
        when(processing.process(claim)).thenReturn(ProcessingOutcome.NOT_APPLIED);
        when(claims.findState(123)).thenReturn(Optional.of(state(DailySongJobStatus.CANCELLED, null, null)));

        consumer.receive(message("{\"jobId\":123}"), channel);

        verify(channel).basicAck(7, false);
    }

    @Test
    void acknowledgesCompletedJobWithoutCallingAi() throws Exception {
        when(claims.findState(123)).thenReturn(Optional.of(state(DailySongJobStatus.COMPLETED, null, null)));

        consumer.receive(message("{\"jobId\":123}"), channel);

        verify(channel).basicAck(7, false);
        verifyNoInteractions(processing);
    }

    @Test
    void rejectsMalformedMessageToDeadLetterQueue() throws Exception {
        consumer.receive(message("{invalid-json}"), channel);

        verify(channel).basicReject(7, false);
        verifyNoInteractions(processing);
    }

    @Test
    void rejectsNonPositiveJobIdToDeadLetterQueue() throws Exception {
        consumer.receive(message("{\"jobId\":0}"), channel);

        verify(channel).basicReject(7, false);
        verifyNoInteractions(processing);
    }

    @Test
    void rejectsUnknownJobToDeadLetterQueue() throws Exception {
        when(claims.findState(123)).thenReturn(Optional.empty());

        consumer.receive(message("{\"jobId\":123}"), channel);

        verify(channel).basicReject(7, false);
        verifyNoInteractions(processing);
    }

    @Test
    void acknowledgesOtherConsumersProcessingJob() throws Exception {
        when(claims.findState(123)).thenReturn(Optional.of(
                state(DailySongJobStatus.PROCESSING, "other-token", now.plusMinutes(5))));

        consumer.receive(message("{\"jobId\":123}"), channel);

        verify(channel).basicAck(7, false);
        verifyNoInteractions(processing);
    }

    @Test
    void acknowledgesCancelledJobWithoutProcessing() throws Exception {
        when(claims.findState(123)).thenReturn(Optional.of(state(DailySongJobStatus.CANCELLED, null, null)));

        consumer.receive(message("{\"jobId\":123}"), channel);

        verify(channel).basicAck(7, false);
        verifyNoInteractions(processing);
    }

    @Test
    void expiredClaimIsRecoveredBeforeAcknowledging() throws Exception {
        when(claims.findState(123)).thenReturn(Optional.of(state(DailySongJobStatus.FAILED, null, null)));
        when(claims.recoverExpired(123, now)).thenReturn(1);

        consumer.receive(message("{\"jobId\":123}"), channel);

        var order = inOrder(claims, channel);
        order.verify(claims).recoverExpired(123, now);
        order.verify(claims).findState(123);
        order.verify(channel).basicAck(7, false);
    }

    @Test
    void lateResponseWithMissingLeaseMovesToDeadLetterQueue() throws Exception {
        Claim claim = new Claim(123, 13, LocalDate.of(2026, 11, 10), now, "old-token");
        when(claims.claim(123, now, now.plusMinutes(5))).thenReturn(Optional.of(claim));
        when(processing.process(claim)).thenReturn(ProcessingOutcome.NOT_APPLIED);
        when(claims.findState(123)).thenReturn(Optional.of(
                state(DailySongJobStatus.PROCESSING, "new-token", null)));

        consumer.receive(message("{\"jobId\":123}"), channel);

        verify(channel).basicReject(7, false);
    }

    @Test
    void databaseStateLookupFailureDoesNotAcknowledge() throws Exception {
        when(claims.findState(123)).thenThrow(new DataAccessResourceFailureException("DB 상태를 조회하지 못했습니다."));

        consumer.receive(message("{\"jobId\":123}"), channel);

        verify(channel).basicNack(7, false, true);
    }

    @Test
    void committedOutcomeWithoutConfirmedTerminalStateMovesToDeadLetterQueue() throws Exception {
        Claim claim = new Claim(123, 13, LocalDate.of(2026, 11, 10), now, "claim-token");
        when(claims.claim(123, now, now.plusMinutes(5))).thenReturn(Optional.of(claim));
        when(processing.process(claim)).thenReturn(ProcessingOutcome.COMPLETED);
        when(claims.findState(123)).thenReturn(Optional.of(
                state(DailySongJobStatus.PROCESSING, claim.token(), now.plusMinutes(5))));

        consumer.receive(message("{\"jobId\":123}"), channel);

        verify(channel).basicReject(7, false);
    }

    @Test
    void requeuesWhenDatabaseCommitIsUncertain() throws Exception {
        Claim claim = new Claim(123, 13, LocalDate.of(2026, 11, 10), now, "claim-token");
        when(claims.claim(123, now, now.plusMinutes(5))).thenReturn(Optional.of(claim));
        doThrow(new DataAccessResourceFailureException("DB 저장 결과를 확인하지 못했습니다."))
                .when(processing).process(claim);

        consumer.receive(message("{\"jobId\":123}"), channel);

        verify(channel).basicNack(7, false, true);
    }

    private JobState state(DailySongJobStatus status, String token, LocalDateTime leaseUntil) {
        return new JobState(status, token, leaseUntil);
    }

    private Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
