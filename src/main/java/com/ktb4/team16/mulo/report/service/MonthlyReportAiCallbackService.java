package com.ktb4.team16.mulo.report.service;

import com.ktb4.team16.mulo.notification.outbox.NotificationOutboxImmediateDispatcher;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 월간 리포트 callback transaction 완료 뒤 알림 처리를 이어 주는 응용 서비스다. */
@Service
@RequiredArgsConstructor
public class MonthlyReportAiCallbackService {

    private final MonthlyReportAiCallbackTransactionalService callback;
    private final NotificationOutboxImmediateDispatcher outboxDispatcher;

    /** callback 저장이 커밋된 뒤 생성된 월간 리포트 알림을 동기 처리한다. */
    public void apply(MonthlyReportAiCallbackRequest request) {
        List<UUID> eventIds = callback.apply(request);
        outboxDispatcher.processAfterTransaction(eventIds);
    }
}
