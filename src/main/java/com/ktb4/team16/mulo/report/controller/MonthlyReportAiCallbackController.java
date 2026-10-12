package com.ktb4.team16.mulo.report.controller;

import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest;
import com.ktb4.team16.mulo.report.service.MonthlyReportAiCallbackService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal/ai")
public class MonthlyReportAiCallbackController {
    private final MonthlyReportAiCallbackService service;

    public MonthlyReportAiCallbackController(MonthlyReportAiCallbackService service) {
        this.service = service;
    }

    // AI 콜백 저장을 위임하고 사용자 실패 또는 트랜잭션 롤백을 운영 로그에 남긴다.
    @PostMapping("/report-ready")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportReady(@Valid @RequestBody MonthlyReportAiCallbackRequest request) {
        try {
            service.apply(request);
        } catch (RuntimeException | Error exception) {
            log.error("월간 리포트 AI 콜백 트랜잭션 롤백: year={}, month={}, resultCount={}",
                    request.year(), request.month(), request.results().size(), exception);
            throw exception;
        }

        for (var result : request.results()) {
            if (result.status() == MonthlyReportAiCallbackRequest.Status.FAILED) {
                log.warn("월간 리포트 사용자 AI 생성 실패: year={}, month={}, userId={}, errorCode={}",
                        request.year(), request.month(), result.userId(), sanitizeErrorCode(result.errorCode()));
            }
        }
    }

    // 외부 AI 오류 코드의 제어 문자를 정리하고 로그 길이를 제한한다.
    private String sanitizeErrorCode(String errorCode) {
        String normalized = errorCode.replaceAll("[^A-Za-z0-9_.-]", "_");
        return normalized.substring(0, Math.min(normalized.length(), 64));
    }
}
