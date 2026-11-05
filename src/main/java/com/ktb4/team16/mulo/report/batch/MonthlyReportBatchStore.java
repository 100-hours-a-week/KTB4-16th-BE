package com.ktb4.team16.mulo.report.batch;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/** 고정 대상·월별 소유권·전송 일지를 업무 DB에 보존한다. 호출자가 짧은 트랜잭션을 소유한다. */
@Repository
public class MonthlyReportBatchStore {
    private final JdbcTemplate jdbc;

    /** JPA와 같은 업무 DataSource의 JDBC 접근을 사용한다. */
    public MonthlyReportBatchStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 같은 ID의 요청 조건을 확인하고 월별 실행을 한 소유자에게 예약한다. */
    public boolean acquire(String key, YearMonth month, String kind) {
        jdbc.update("INSERT IGNORE INTO report_batch_months(target_month) VALUES(?)", month.toString());
        String owner = jdbc.queryForObject("SELECT COALESCE(owner_run_key,'') FROM report_batch_months "
                + "WHERE target_month=? FOR UPDATE", String.class, month.toString());
        jdbc.update("INSERT IGNORE INTO report_batch_runs(run_key,target_month,kind) VALUES(?,?,?)",
                key, month.toString(), kind);
        var row = run(key);
        if (!month.toString().equals(row.month()) || !kind.equals(row.kind())) {
            throw new ReportBatchException("REPORT_REQUEST_CONFLICT");
        }
        if ("COMPLETE".equals(row.status())) {
            return false;
        }
        // READY/FAILED만 시작할 수 있다. 같은 실행의 동시 진입도 허용하지 않는다.
        if (!owner.isEmpty() || "RUNNING".equals(row.status())) {
            throw new ReportBatchException("REPORT_ALREADY_RUNNING");
        }
        jdbc.update("UPDATE report_batch_months SET owner_run_key=? WHERE target_month=?", key, month.toString());
        jdbc.update("UPDATE report_batch_runs SET status='RUNNING' WHERE run_key=?", key);
        return true;
    }

    /** 완료 결과를 유지하고 실패 시에도 고정 대상·전송 기록은 삭제하지 않는다. */
    public void finish(String key, boolean success, Long executionId) {
        String month = jdbc.queryForObject("SELECT target_month FROM report_batch_runs WHERE run_key=?",
                String.class, key);
        // acquire/reconcile과 같은 월→실행 잠금 순서를 사용해 종료 경합의 교착을 막는다.
        jdbc.update("UPDATE report_batch_months SET owner_run_key=NULL WHERE target_month=? AND owner_run_key=?",
                month, key);
        jdbc.update("UPDATE report_batch_runs SET status=?,execution_id=? WHERE run_key=?",
                success ? "COMPLETE" : "FAILED", executionId, key);
    }

    /** 실행 조건·집계 건수를 복원한다. */
    public Run run(String key) {
        try {
            return jdbc.queryForObject("""
                    SELECT r.target_month,r.kind,r.status,r.targets_fixed,r.created_count,r.skipped_count,
                           COALESCE((
                               SELECT e.JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION e
                               JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID=e.JOB_EXECUTION_ID
                               WHERE p.PARAMETER_NAME='runKey' AND p.PARAMETER_VALUE=r.run_key
                               ORDER BY e.JOB_EXECUTION_ID DESC LIMIT 1
                           ),r.execution_id) AS resolved_execution_id
                    FROM report_batch_runs r WHERE r.run_key=?
                    """, (rs, n) -> {
                long id = rs.getLong("resolved_execution_id");
                Long executionId = rs.wasNull() ? null : id;
                return new Run(rs.getString("target_month"), rs.getString("kind"), rs.getString("status"),
                        rs.getBoolean("targets_fixed"), rs.getInt("created_count"), rs.getInt("skipped_count"),
                        executionId);
            }, key);
        } catch (EmptyResultDataAccessException exception) {
            throw new ReportBatchException("REPORT_RUN_NOT_FOUND");
        }
    }

    /** 명단은 최초 한 번만 확정하며 실패 재시작에서는 변동한 원본을 다시 읽지 않는다. */
    public void fixTargets(String key) {
        Run run = run(key);
        if (run.fixed()) {
            return;
        }
        YearMonth month = YearMonth.parse(run.month());
        if ("RETRY".equals(run.kind())) {
            // 콜백으로 확인한 실패만 선택한다. 과거 통신 오류 FAILED나 원본 기록 존재 여부에 의존하지 않는다.
            jdbc.update("""
                    INSERT INTO report_batch_targets(run_key,user_id)
                    SELECT ?,m.user_id FROM monthly_reports m
                    JOIN report_ai_dispatches d ON d.batch_request_id=m.batch_request_id
                    JOIN report_batch_targets t ON t.run_key=d.run_key AND t.user_id=m.user_id
                    JOIN users u ON u.user_id=m.user_id
                    WHERE m.report_year=? AND m.report_month=? AND m.ai_recap_status='FAILED'
                      AND t.callback_status='FAILED' AND u.deleted_at IS NULL
                    """, key, month.getYear(), month.getMonthValue());
        } else {
            jdbc.update("""
                    INSERT INTO report_batch_targets(run_key,user_id)
                    SELECT DISTINCT ?,r.user_id FROM records r JOIN users u ON u.user_id=r.user_id
                    WHERE r.deleted_at IS NULL AND u.deleted_at IS NULL AND r.created_at>=? AND r.created_at<?
                    """, key, month.atDay(1).atStartOfDay(),
                    month.plusMonths(1).atDay(1).atStartOfDay());
        }
        jdbc.update("UPDATE report_batch_runs SET targets_fixed=TRUE WHERE run_key=?", key);
    }

    /** 진행하지 않은 명단을 ID 순서로 최대 500명 읽는다. NEW 상태 자체가 영속 체크포인트다. */
    public List<Long> nextTargets(String key) {
        return jdbc.queryForList("SELECT user_id FROM report_batch_targets WHERE run_key=? AND state='NEW' "
                + "ORDER BY user_id LIMIT 500", Long.class, key);
    }

    /** 집계/재요청 가능한 리포트만 READY로 표시하고 처리 건수를 같은 커밋에 기록한다. */
    public void prepared(String key, List<Long> chunk, List<Long> ready, int created, int skipped) {
        for (Long id : chunk) {
            jdbc.update("UPDATE report_batch_targets SET state=? WHERE run_key=? AND user_id=?",
                    ready.contains(id) ? "READY" : "SKIPPED", key, id);
        }
        jdbc.update("UPDATE report_batch_runs SET created_count=created_count+?,skipped_count=skipped_count+? "
                + "WHERE run_key=?", created, skipped, key);
    }

    /** 미전송 대상과 단일 AI 전송 ID를 함께 예약한다. 이미 예약한 ID는 재사용한다. */
    public void reserve(String key) {
        if (dispatch(key) != null) {
            return;
        }
        Run run = run(key);
        YearMonth month = YearMonth.parse(run.month());
        List<Long> ready = readyUsers(key);
        List<Long> sendable = new java.util.ArrayList<>();
        for (Long user : ready) {
            // 재요청 대상을 고정한 뒤 늦은 성공 콜백이 온 사용자는 재전송하지 않는다.
            String state = jdbc.queryForObject("SELECT ai_recap_status FROM monthly_reports "
                    + "WHERE user_id=? AND report_year=? AND report_month=? FOR UPDATE",
                    String.class, user, month.getYear(), month.getMonthValue());
            if ("RETRY".equals(run.kind()) && "COMPLETED".equals(state)) {
                jdbc.update("UPDATE report_batch_targets SET state='SKIPPED' WHERE run_key=? AND user_id=?",
                        key, user);
                jdbc.update("UPDATE report_batch_runs SET skipped_count=skipped_count+1 WHERE run_key=?", key);
                continue;
            }
            String expected = "RETRY".equals(run.kind()) ? "FAILED" : "PENDING";
            if (!expected.equals(state)) {
                throw new ReportBatchException("REPORT_REQUEST_CONFLICT");
            }
            sendable.add(user);
        }
        if (sendable.isEmpty()) {
            return;
        }
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO report_ai_dispatches(batch_request_id,run_key) VALUES(?,?)", id, key);
        for (Long user : sendable) {
            jdbc.update("UPDATE monthly_reports SET ai_recap_status='PENDING',batch_request_id=? "
                    + "WHERE user_id=? AND report_year=? AND report_month=?", id, user,
                    month.getYear(), month.getMonthValue());
        }
    }

    /** 고정된 전송 명단을 복원한다. HTTP 본문 계약이 목록이므로 이 단계에서만 목록을 구체화한다. */
    public List<Long> readyUsers(String key) {
        return jdbc.queryForList("SELECT user_id FROM report_batch_targets WHERE run_key=? AND state='READY' "
                + "ORDER BY user_id", Long.class, key);
    }

    /** 실행에 예약된 전송 기록을 조회한다. */
    public Dispatch dispatch(String key) {
        var rows = jdbc.query("SELECT batch_request_id,state FROM report_ai_dispatches WHERE run_key=?",
                (rs, n) -> new Dispatch(rs.getString(1), rs.getString(2)), key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 원자적 전송 시작 표시를 먼저 커밋하여 종료 직후의 재전송을 막는다. */
    public boolean claim(String key) {
        return jdbc.update("UPDATE report_ai_dispatches SET state='SENDING',sent_at=CURRENT_TIMESTAMP(6) "
                + "WHERE run_key=? AND state IN ('PREPARED','NOT_ACCEPTED')", key) == 1;
    }

    /** 접수 불명확을 보존하되 이미 유효한 콜백으로 접수 확인된 결과는 되돌리지 않는다. */
    public void unknown(String key) {
        jdbc.update("UPDATE report_ai_dispatches SET state='UNKNOWN' WHERE run_key=? AND state='SENDING'", key);
    }

    /** 접수 응답·콜백을 접수 증거로 반영하고 완료/실패한 사용자 결과는 그대로 둔다. */
    public void accepted(String key) {
        // 콜백과 동일한 report→dispatch 잠금 순서를 지켜 빠른 콜백과의 교착을 피한다.
        lockReports(key);
        jdbc.update("UPDATE report_ai_dispatches SET state='ACCEPTED' WHERE run_key=?", key);
        jdbc.update("""
                UPDATE monthly_reports m JOIN report_ai_dispatches d ON m.batch_request_id=d.batch_request_id
                SET m.ai_recap_status='PROCESSING' WHERE d.run_key=? AND m.ai_recap_status='PENDING'
                """, key);
    }

    /** 일지가 있는 신규 콜백의 결과 지문을 기록하고 상충한 최종 결과를 거절한다. */
    public boolean recordCallback(String id, Long userId, String outcome, String fingerprint) {
        var rows = jdbc.query("""
                SELECT t.run_key,t.callback_status,t.callback_fingerprint FROM report_batch_targets t
                JOIN report_ai_dispatches d ON d.run_key=t.run_key
                WHERE d.batch_request_id=? AND t.user_id=? FOR UPDATE
                """, (rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, id, userId);
        if (rows.isEmpty()) {
            return true; // 전송 일지 도입 전 리포트의 기존 콜백 호환.
        }
        String[] row = rows.getFirst();
        if ("COMPLETED".equals(row[1])) {
            if ("FAILED".equals(outcome) || fingerprint.equals(row[2])) {
                return false;
            }
            throw new ReportBatchException("REPORT_CALLBACK_CONFLICT");
        }
        if ("FAILED".equals(row[1]) && "FAILED".equals(outcome)) {
            return false;
        }
        jdbc.update("UPDATE report_batch_targets SET callback_status=?,callback_fingerprint=? "
                + "WHERE run_key=? AND user_id=?", outcome, fingerprint, row[0], userId);
        jdbc.update("UPDATE report_ai_dispatches SET state='ACCEPTED' WHERE run_key=?", row[0]);
        return true;
    }

    /** 운영 확인은 근거와 담당자를 남기며 UNKNOWN/SENDING 요청에만 적용한다. */
    public void reconcile(String id, boolean accepted, String actor, String note) {
        String key;
        try {
            key = jdbc.queryForObject("SELECT run_key FROM report_ai_dispatches WHERE batch_request_id=?",
                    String.class, id);
        } catch (EmptyResultDataAccessException exception) {
            throw new ReportBatchException("REPORT_DISPATCH_NOT_FOUND");
        }
        if ("RUNNING".equals(lockRunStatus(key))) {
            throw new ReportBatchException("REPORT_ALREADY_RUNNING");
        }
        lockReports(key);
        int updated = jdbc.update("UPDATE report_ai_dispatches SET state=?,confirmed_by=?,confirmation_note=?,"
                + "confirmed_at=CURRENT_TIMESTAMP(6) WHERE batch_request_id=? AND state IN ('UNKNOWN','SENDING')",
                accepted ? "ACCEPTED" : "NOT_ACCEPTED", actor, note, id);
        if (updated != 1) {
            throw new ReportBatchException("REPORT_REQUEST_CONFLICT");
        }
        if (accepted) {
            accepted(key);
        }
    }

    /** Job 재시작과 운영 접수 확인을 월 소유권 행부터 직렬화한다. */
    public String lockRunStatus(String key) {
        String month;
        try {
            month = jdbc.queryForObject("SELECT target_month FROM report_batch_runs WHERE run_key=?",
                    String.class, key);
        } catch (EmptyResultDataAccessException exception) {
            throw new ReportBatchException("REPORT_RUN_NOT_FOUND");
        }
        jdbc.queryForObject("SELECT target_month FROM report_batch_months WHERE target_month=? FOR UPDATE",
                String.class, month);
        return jdbc.queryForObject("SELECT status FROM report_batch_runs WHERE run_key=? FOR UPDATE",
                String.class, key);
    }

    /** 콜백과 접수 확인은 같은 사용자 ID 순서로 리포트 행을 먼저 잠근다. */
    private void lockReports(String key) {
        jdbc.queryForList("SELECT m.monthly_report_id FROM monthly_reports m "
                + "JOIN report_ai_dispatches d ON d.batch_request_id=m.batch_request_id "
                + "WHERE d.run_key=? ORDER BY m.user_id FOR UPDATE", Long.class, key);
    }

    /** 실행 조건과 결과는 DB가 원본이며 메모리 인스턴스 수에 의존하지 않는다. */
    public record Run(String month, String kind, String status, boolean fixed, int created, int skipped,
            Long executionId) { }
    /** AI 작업 식별자는 외부 jobId가 아닌 백엔드 요청 ID다. */
    public record Dispatch(String id, String state) { }
}
