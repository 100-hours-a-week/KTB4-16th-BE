package com.ktb4.team16.mulo.report.batch;

import com.ktb4.team16.mulo.report.service.MonthlyReportGenerationService.GenerationResult;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.ExitStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 운영 요청을 영속 작업으로 식별하고 같은 월·재요청의 중복 실행을 조정한다. */
@Service
public class MonthlyReportJobService {
    private final MonthlyReportBatchStore store;
    private final JobOperator operator;
    private final Job generateJob;
    private final Job retryJob;
    private final TransactionTemplate tx;
    private final JobRepository repository;
    private final JdbcTemplate jdbc;

    /** 기본 JobOperator는 동기 실행이며 업무 예약 트랜잭션은 시작 전에 끝낸다. */
    public MonthlyReportJobService(MonthlyReportBatchStore store, JobOperator operator,
            @Qualifier("monthlyReportGenerateJob") Job generateJob,
            @Qualifier("monthlyReportAiRetryJob") Job retryJob,
            PlatformTransactionManager transactionManager, JobRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.store = store;
        this.operator = operator;
        this.generateJob = generateJob;
        this.retryJob = retryJob;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 정기·수동 생성은 같은 월 키로 실행하고 테스트는 운영 식별자와 분리한다. */
    public GenerationResult generate(YearMonth month, boolean test) {
        return launch((test ? "TEST:" : "GENERATE:") + month, month, test ? "TEST" : "GENERATE", generateJob);
    }

    /** 같은 재요청 UUID를 재전달해도 최초 명단과 실행 결과를 재사용한다. */
    public GenerationResult retry(YearMonth month, String retryRequestId) {
        String canonical;
        try {
            canonical = UUID.fromString(retryRequestId).toString();
        } catch (RuntimeException exception) {
            throw new ReportBatchException("REPORT_INVALID_REQUEST");
        }
        return launch("RETRY:" + canonical, month, "RETRY", retryJob);
    }

    /** 기존 FAILED Job만 같은 명단·batchRequestId로 재개하며 UNKNOWN은 먼저 운영 확인을 요구한다. */
    public GenerationResult resume(String key) {
        var run = store.run(key);
        YearMonth month = YearMonth.parse(run.month());
        if ("COMPLETE".equals(run.status())) {
            return result(key, month);
        }
        if (!"FAILED".equals(run.status())) {
            throw new ReportBatchException("REPORT_REQUEST_CONFLICT");
        }
        var dispatch = store.dispatch(key);
        if (dispatch != null && ("UNKNOWN".equals(dispatch.state()) || "SENDING".equals(dispatch.state()))) {
            throw new ReportBatchException("AI_ACCEPTANCE_UNKNOWN");
        }
        Job job = switch (run.kind()) {
            case "GENERATE" -> generateJob;
            case "RETRY" -> retryJob;
            default -> throw new ReportBatchException("REPORT_REQUEST_CONFLICT");
        };
        return launch(key, month, run.kind(), job);
    }

    /** Job 시작과 HTTP를 업무 트랜잭션 안에 넣지 않고 결과 건수를 DB에서 복원한다. */
    private GenerationResult launch(String key, YearMonth month, String kind, Job job) {
        boolean acquired = Boolean.TRUE.equals(tx.execute(status -> store.acquire(key, month, kind)));
        if (!acquired) {
            return result(key, month);
        }
        JobExecution execution = null;
        boolean success = false;
        try {
            var parameters = new JobParametersBuilder().addString("runKey", key)
                    .addString("targetMonth", month.toString()).toJobParameters();
            var previous = repository.getLastJobExecution(job.getName(), parameters);
            if (previous != null && previous.getStatus() == BatchStatus.COMPLETED) {
                // 프로세스가 Batch 완료 직후 업무 COMPLETE 저장 전에 종료된 경우다.
                execution = previous;
                success = true;
                return result(key, month);
            }
            execution = operator.start(job, parameters);
            success = execution.getStatus() == BatchStatus.COMPLETED;
            if (!success) {
                var dispatch = store.dispatch(key);
                throw new ReportBatchException(dispatch != null && ("UNKNOWN".equals(dispatch.state())
                        || "SENDING".equals(dispatch.state())) ? "AI_ACCEPTANCE_UNKNOWN" : "REPORT_BATCH_FAILED");
            }
            return result(key, month);
        } catch (ReportBatchException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ReportBatchException("REPORT_BATCH_FAILED");
        } finally {
            boolean completed = success;
            Long executionId = execution == null ? null : execution.getId();
            tx.executeWithoutResult(status -> store.finish(key, completed, executionId));
        }
    }

    /** 운영자가 프로세스 종료를 확인한 실행만 실패로 복구하며 AI는 호출하지 않는다. */
    public void recoverStopped(String key, Long executionId, boolean stoppedConfirmed, String actor, String evidence) {
        if (!stoppedConfirmed || actor == null || actor.isBlank() || evidence == null || evidence.isBlank()) {
            throw new ReportBatchException("REPORT_INVALID_REQUEST");
        }
        tx.executeWithoutResult(status -> {
            // 재시작·접수 확인과 동일한 월→실행 잠금 순서를 유지한다.
            store.lockRunStatus(key);
            var run = store.run(key);
            if (executionId == null) {
                if (!"RUNNING".equals(run.status()) || run.executionId() != null) {
                    throw new ReportBatchException("REPORT_REQUEST_CONFLICT");
                }
                Job job = switch (run.kind()) {
                    case "GENERATE" -> generateJob;
                    case "RETRY" -> retryJob;
                    default -> throw new ReportBatchException("REPORT_REQUEST_CONFLICT");
                };
                var parameters = new JobParametersBuilder().addString("runKey", key)
                        .addString("targetMonth", run.month()).toJobParameters();
                if (repository.getLastJobExecution(job.getName(), parameters) != null) {
                    throw new ReportBatchException("REPORT_REQUEST_CONFLICT");
                }
                // JobRepository 메타데이터가 아직 없는 고아 예약은 업무 실행만 실패로 풀어준다.
                jdbc.update("INSERT IGNORE INTO report_batch_recoveries(execution_id,run_key,actor,evidence) "
                        + "VALUES(NULL,?,?,?)", key, actor, evidence);
                store.unknown(key);
                store.finish(key, false, null);
                return;
            }
            var execution = repository.getJobExecution(executionId);
            if (execution == null || !key.equals(execution.getJobParameters().getString("runKey"))
                    || execution.getStatus() == BatchStatus.COMPLETED
                    || !executionId.equals(repository.getLastJobExecution(execution.getJobInstance()).getId())) {
                throw new ReportBatchException("REPORT_REQUEST_CONFLICT");
            }
            // 시간만으로 회수하지 않는다. 종료 확인 책임과 증거를 감사 기록에 남긴다.
            jdbc.update("INSERT IGNORE INTO report_batch_recoveries(execution_id,run_key,actor,evidence) VALUES(?,?,?,?)",
                    executionId, key, actor, evidence);
            for (var step : execution.getStepExecutions()) {
                if (step.getStatus().isRunning()) {
                    step.setStatus(BatchStatus.FAILED);
                    step.setEndTime(java.time.LocalDateTime.now());
                    step.setExitStatus(new ExitStatus("REPORT_OPERATOR_RECOVERY"));
                    repository.update(step);
                }
            }
            execution.setStatus(BatchStatus.FAILED);
            execution.setEndTime(java.time.LocalDateTime.now());
            execution.setExitStatus(new ExitStatus("REPORT_OPERATOR_RECOVERY"));
            repository.update(execution);
            store.unknown(key);
            store.finish(key, false, executionId);
        });
    }

    /** 중복 완료 요청에도 최초 실행의 생성/건너뜀 건수를 반환한다. */
    private GenerationResult result(String key, YearMonth month) {
        var run = store.run(key);
        return new GenerationResult(month, run.created(), run.skipped());
    }
}
