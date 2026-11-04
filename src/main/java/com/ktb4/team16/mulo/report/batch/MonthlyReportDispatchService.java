package com.ktb4.team16.mulo.report.batch;

import com.ktb4.team16.mulo.report.client.MonthlyReportBatchAiClient;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 최초 전송과 확인된 미접수 복구만 허용하고 HTTP 중 DB 연결을 점유하지 않는다. */
@Service
public class MonthlyReportDispatchService {
    private final MonthlyReportBatchStore store;
    private final MonthlyReportBatchAiClient ai;
    private final TransactionTemplate tx;

    /** 전송 상태 변경만 짧은 업무 트랜잭션으로 묶는다. */
    public MonthlyReportDispatchService(MonthlyReportBatchStore store, MonthlyReportBatchAiClient ai,
            PlatformTransactionManager transactionManager) {
        this.store = store;
        this.ai = ai;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 시작 표시 후 HTTP를 보내며 timeout·결과 불명확에서는 같은 ID를 보존한다. */
    public void send(String key) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("AI dispatch must run outside a database transaction");
        }
        var dispatch = store.dispatch(key);
        if (dispatch == null || "ACCEPTED".equals(dispatch.state())) {
            return;
        }
        if (!Boolean.TRUE.equals(tx.execute(status -> store.claim(key)))) {
            tx.executeWithoutResult(status -> store.unknown(key));
            throw new ReportBatchException("AI_ACCEPTANCE_UNKNOWN");
        }
        try {
            var run = store.run(key);
            ai.requestBatch(YearMonth.parse(run.month()), store.readyUsers(key), dispatch.id());
            tx.executeWithoutResult(status -> store.accepted(key));
        } catch (RuntimeException exception) {
            // 저장 실패도 접수 실패와 같지 않다. 유효한 콜백이 도착했으면 ACCEPTED를 보존한다.
            tx.executeWithoutResult(status -> store.unknown(key));
            if (!"ACCEPTED".equals(store.dispatch(key).state())) {
                throw new ReportBatchException("AI_ACCEPTANCE_UNKNOWN");
            }
        }
    }

    /** 운영자가 AI 기록으로 확인한 사실과 근거만 반영하고 즉시 재전송하지 않는다. */
    public void reconcile(String id, boolean accepted, String actor, String note) {
        tx.executeWithoutResult(status -> store.reconcile(id, accepted, actor, note));
    }
}
