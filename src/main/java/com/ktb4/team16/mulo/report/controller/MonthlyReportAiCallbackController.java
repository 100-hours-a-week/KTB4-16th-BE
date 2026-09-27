package com.ktb4.team16.mulo.report.controller;
import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest;
import com.ktb4.team16.mulo.report.service.MonthlyReportAiCallbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/internal/ai") public class MonthlyReportAiCallbackController {
 private final MonthlyReportAiCallbackService service;
 public MonthlyReportAiCallbackController(MonthlyReportAiCallbackService service){this.service=service;}
 // AI 완료 콜백을 받아 저장 서비스에 위임하고 성공 시 본문 없이 응답한다.
 @PostMapping("/report-ready") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void reportReady(@Valid @RequestBody MonthlyReportAiCallbackRequest request){service.apply(request);}
}
