package com.ktb4.team16.mulo.report.batch;

import com.ktb4.team16.mulo.report.service.MonthlyReportPreparationChunkService;
import java.time.YearMonth;
import java.util.List;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

/** JDBC 실행 이력과 업무 데이터를 같은 트랜잭션 관리자로 처리하는 두 Job을 구성한다. */
@Configuration(proxyBeanMethods = false)
@EnableBatchProcessing
@EnableJdbcJobRepository(dataSourceRef = "dataSource", transactionManagerRef = "transactionManager")
public class MonthlyReportBatchConfiguration {
    private final JobRepository repository;
    private final PlatformTransactionManager transactionManager;
    private final MonthlyReportBatchStore store;
    private final MonthlyReportPreparationChunkService preparation;
    private final MonthlyReportDispatchService dispatch;

    /** 생성·재요청은 도메인 집계와 전송 일지 구성 요소를 공유한다. */
    public MonthlyReportBatchConfiguration(JobRepository repository, PlatformTransactionManager transactionManager,
            MonthlyReportBatchStore store, MonthlyReportPreparationChunkService preparation,
            MonthlyReportDispatchService dispatch) {
        this.repository = repository;
        this.transactionManager = transactionManager;
        this.store = store;
        this.preparation = preparation;
        this.dispatch = dispatch;
    }

    /** 월 마감 생성의 고정 대상·집계·예약·접수를 순서대로 실행한다. */
    @Bean
    public Job monthlyReportGenerateJob() {
        return build("monthlyReportGenerateJob");
    }

    /** 실패 확정 리포트만 재요청하며 집계 단계에서는 기존 스냅샷을 수정하지 않는다. */
    @Bean
    public Job monthlyReportAiRetryJob() {
        return build("monthlyReportAiRetryJob");
    }

    /** 두 Job의 Step 이름을 분리하여 실행·재시작 이력을 보존한다. */
    private Job build(String name) {
        return new JobBuilder(name, repository).start(targets(name)).next(prepare(name))
                .next(reserve(name)).next(send(name)).listener(new JobExecutionListener() {
                    /** 불명확 접수의 업무 종료 사유를 Job 이력에도 보존한다. */
                    @Override
                    public void afterJob(JobExecution execution) {
                        var entry = store.dispatch(execution.getJobParameters().getString("runKey"));
                        if (entry != null && ("UNKNOWN".equals(entry.state()) || "SENDING".equals(entry.state()))) {
                            execution.setExitStatus(new ExitStatus("AI_ACCEPTANCE_UNKNOWN"));
                        }
                    }
                }).build();
    }

    /** 최초 대상 명단 저장과 완료 표시를 단일 트랜잭션으로 커밋한다. */
    private Step targets(String name) {
        return new StepBuilder(name + ".targets", repository).tasklet((contribution, context) -> {
            store.fixTargets(key(contribution.getStepExecution()));
            return RepeatStatus.FINISHED;
        }, transactionManager).build();
    }

    /** 500명씩 처리하고 업무 진행 상태와 Batch 체크포인트를 같은 커밋에 기록한다. */
    private Step prepare(String name) {
        return new StepBuilder(name + ".prepare", repository).tasklet((contribution, context) -> {
            var execution = contribution.getStepExecution();
            String key = key(execution);
            List<Long> ids = store.nextTargets(key);
            if (ids.isEmpty()) {
                return RepeatStatus.FINISHED;
            }
            var run = store.run(key);
            List<Long> ready;
            int created = 0;
            int skipped = 0;
            if ("RETRY".equals(run.kind())) {
                ready = ids;
            } else {
                var result = preparation.prepareForBatch(YearMonth.parse(run.month()), ids);
                ready = result.userIds();
                created = result.createdCount();
                skipped = result.skippedCount();
            }
            store.prepared(key, ids, ready, created, skipped);
            execution.getExecutionContext().putLong("lastUserId", ids.getLast());
            contribution.incrementWriteCount(ready.size());
            return RepeatStatus.CONTINUABLE;
        }, transactionManager).build();
    }

    /** 요청 ID와 대상 리포트 예약은 HTTP 전에 커밋한다. */
    private Step reserve(String name) {
        return new StepBuilder(name + ".reserve", repository).tasklet((contribution, context) -> {
            store.reserve(key(contribution.getStepExecution()));
            return RepeatStatus.FINISHED;
        }, transactionManager).build();
    }

    /** Tasklet 기본 트랜잭션을 중지하여 외부 AI 대기 중 행 잠금·연결을 점유하지 않는다. */
    private Step send(String name) {
        var attribute = new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        return new StepBuilder(name + ".send", repository).tasklet((contribution, context) -> {
            dispatch.send(key(contribution.getStepExecution()));
            return RepeatStatus.FINISHED;
        }, transactionManager).transactionAttribute(attribute).listener(new StepExecutionListener() {
            /** 불명확 접수를 기술 오류와 구분하는 업무 종료 사유를 기록한다. */
            @Override
            public ExitStatus afterStep(StepExecution execution) {
                boolean unknown = execution.getFailureExceptions().stream().anyMatch(error ->
                        error instanceof ReportBatchException failure
                                && "AI_ACCEPTANCE_UNKNOWN".equals(failure.code()));
                return unknown ? new ExitStatus("AI_ACCEPTANCE_UNKNOWN") : execution.getExitStatus();
            }
        }).build();
    }

    /** 런 키는 메모리 상태가 아닌 영속 업무 실행을 참조한다. */
    private String key(StepExecution execution) {
        return execution.getJobParameters().getString("runKey");
    }
}
