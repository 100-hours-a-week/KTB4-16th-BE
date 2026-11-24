package com.ktb4.team16.mulo.dailysong.consumer;

import com.ktb4.team16.mulo.dailysong.config.DailySongRabbitMqTopologyConfiguration;
import com.ktb4.team16.mulo.dailysong.entity.DailySongJobStatus;
import com.ktb4.team16.mulo.dailysong.publisher.DailySongJobMessage;
import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository;
import com.ktb4.team16.mulo.dailysong.repository.DailySongJobClaimRepository.JobState;
import com.ktb4.team16.mulo.dailysong.service.DailySongJobProcessingService;
import com.ktb4.team16.mulo.dailysong.service.DailySongJobProcessingService.ProcessingOutcome;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "mulo.rabbitmq", name = "enabled", havingValue = "true")
public class DailySongRabbitMqConsumer {
    private static final Logger log = LoggerFactory.getLogger(DailySongRabbitMqConsumer.class);

    private final ObjectMapper objectMapper;
    private final DailySongJobClaimRepository claims;
    private final DailySongJobProcessingService processing;
    private final Clock clock;

    public DailySongRabbitMqConsumer(ObjectMapper objectMapper, DailySongJobClaimRepository claims,
            DailySongJobProcessingService processing, Clock clock) {
        this.objectMapper = objectMapper;
        this.claims = claims;
        this.processing = processing;
        this.clock = clock;
    }

    @RabbitListener(queues = DailySongRabbitMqTopologyConfiguration.WORK_QUEUE_NAME,
            containerFactory = "dailySongListenerContainerFactory")
    public void receive(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        DailySongJobMessage payload;
        try {
            payload = objectMapper.readValue(message.getBody(), DailySongJobMessage.class);
            if (payload == null || payload.jobId() <= 0) {
                logDlqReject(messageId, payload == null ? null : payload.jobId(), "INVALID_PAYLOAD",
                        payload == null ? null : new IllegalArgumentException("jobId가 올바르지 않습니다."), null, null);
                channel.basicReject(tag, false);
                return;
            }
        } catch (JacksonException | IllegalArgumentException exception) {
            logDlqReject(messageId, null, "INVALID_PAYLOAD", exception, null, null);
            channel.basicReject(tag, false);
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            claims.cancelPendingWithdrawn(payload.jobId(), now);
            Optional<DailySongJobClaimRepository.Claim> claimed = claims.claim(payload.jobId(), now,
                    now.plusMinutes(5));
            ProcessingOutcome outcome = ProcessingOutcome.NOT_APPLIED;
            if (claimed.isPresent()) {
                outcome = processing.process(claimed.get());
            }
            settleDelivery(channel, tag, messageId, payload.jobId(), outcome);
        } catch (RuntimeException exception) {
            log.error("오늘의 노래 작업 처리 상태가 불확실하여 메시지를 재전달합니다: jobId={}",
                    payload.jobId(), exception);
            channel.basicNack(tag, false, true);
        }
    }

    private void settleDelivery(Channel channel, long tag, String messageId, long jobId, ProcessingOutcome outcome)
            throws IOException {
        LocalDateTime now = LocalDateTime.now(clock);
        if (outcome == ProcessingOutcome.NOT_APPLIED) {
            claims.recoverExpired(jobId, now);
        }
        Optional<JobState> state = claims.findState(jobId);
        if (state.isEmpty()) {
            logDlqReject(messageId, jobId, "JOB_NOT_FOUND", null, null, outcome);
            channel.basicReject(tag, false);
            return;
        }
        DailySongJobStatus status = state.get().status();
        if (status == DailySongJobStatus.COMPLETED || status == DailySongJobStatus.FAILED
                || status == DailySongJobStatus.CANCELLED) {
            channel.basicAck(tag, false);
        } else if (status == DailySongJobStatus.PENDING) {
            channel.basicNack(tag, false, true);
        } else if (state.get().claimToken() != null && state.get().leaseUntil() != null
                && state.get().leaseUntil().isAfter(now) && outcome == ProcessingOutcome.NOT_APPLIED) {
            channel.basicAck(tag, false);
        } else {
            logDlqReject(messageId, jobId, "JOB_STATE_UNCERTAIN", null, state.get(), outcome);
            channel.basicReject(tag, false);
        }
    }

    private void logDlqReject(String messageId, Long jobId, String failureType, Throwable exception,
            JobState state, ProcessingOutcome outcome) {
        Throwable rootCause = exception;
        while (rootCause != null && rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        String exceptionType = exception == null ? null : exception.getClass().getSimpleName();
        String rootCauseType = rootCause == null ? null : rootCause.getClass().getSimpleName();
        DailySongJobStatus status = state == null ? null : state.status();
        LocalDateTime leaseUntil = state == null ? null : state.leaseUntil();
        String occurredAt = clock.instant().toString();
        String format = "오늘의 노래 메시지 DLQ 이동 요청: messageId={}, jobId={}, failureType={}, "
                + "occurredAt={}, exceptionType={}, rootCauseType={}, status={}, outcome={}, leaseUntil={}";
        if ("JOB_STATE_UNCERTAIN".equals(failureType)) {
            log.error(format, messageId, jobId, failureType, occurredAt, exceptionType, rootCauseType,
                    status, outcome, leaseUntil);
        } else {
            log.warn(format, messageId, jobId, failureType, occurredAt, exceptionType, rootCauseType,
                    status, outcome, leaseUntil);
        }
    }
}
