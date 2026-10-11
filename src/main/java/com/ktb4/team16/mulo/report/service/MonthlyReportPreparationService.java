package com.ktb4.team16.mulo.report.service;

import com.ktb4.team16.mulo.report.entity.MonthlyReport;
import com.ktb4.team16.mulo.report.repository.MonthlyReportRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonthlyReportPreparationService {
    // 롤백에 영향을 받는 최대 인원을 1,000명보다 낮추기 위해 선택한 사용자 기준이다.
    private static final int USER_CHUNK_SIZE = 500;

    private final MonthlyReportRepository reports;
    private final MonthlyReportPreparationChunkService chunkService;

    public MonthlyReportPreparationService(MonthlyReportRepository reports,
            MonthlyReportPreparationChunkService chunkService) {
        this.reports = reports;
        this.chunkService = chunkService;
    }

    // 활성 사용자 목록을 500명씩 분리해 각 묶음의 스냅샷 준비를 위임한다.
    public PreparedBatch prepare(YearMonth month) {
        List<Long> activeUserIds = chunkService.findActiveUserIds(month);
        List<Long> preparedUserIds = new ArrayList<>();
        int created = 0;
        int skipped = 0;

        for (int offset = 0; offset < activeUserIds.size(); offset += USER_CHUNK_SIZE) {
            int limit = Math.min(offset + USER_CHUNK_SIZE, activeUserIds.size());
            List<Long> userChunk = List.copyOf(activeUserIds.subList(offset, limit));
            MonthlyReportPreparationChunkService.PreparedChunk chunk =
                    chunkService.prepareChunk(month, userChunk);
            preparedUserIds.addAll(chunk.userIds());
            created += chunk.createdCount();
            skipped += chunk.skippedCount();
        }

        return new PreparedBatch(month, List.copyOf(preparedUserIds), created, skipped);
    }

    // AI가 배치 생성을 접수한 뒤 준비된 리포트만 PROCESSING으로 전환한다.
    @Transactional
    public void markProcessing(PreparedBatch batch) {
        changeStatus(batch, MonthlyReport::markProcessing);
    }

    // AI 요청이 실패하면 준비된 리포트의 집계 스냅샷은 남기고 FAILED로 전환한다.
    @Transactional
    public void markFailed(PreparedBatch batch) {
        changeStatus(batch, MonthlyReport::markFailedUnlessCompleted);
    }

    // 배치 대상 사용자 리포트를 조회해 전달받은 상태 변경을 적용한다.
    private void changeStatus(PreparedBatch batch, Consumer<MonthlyReport> change) {
        short year = (short) batch.targetMonth().getYear();
        short month = (short) batch.targetMonth().getMonthValue();
        for (Long userId : batch.userIds()) {
            reports.findByUser_UserIdAndReportYearAndReportMonth(userId, year, month)
                    .ifPresent(change);
        }
    }

    public record PreparedBatch(YearMonth targetMonth, List<Long> userIds, int createdCount,
            int skippedCount) { }
}
