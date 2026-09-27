package com.ktb4.team16.mulo.report.dto.response;

import com.ktb4.team16.mulo.report.service.MonthlyReportQueryService.MonthlyReportDetail;
import java.util.List;

public record MonthlyReportDetailResponse(String message, Data data) {
    public record Data(Long monthlyReportId, int year, int month, Stats stats,
            List<PhotoScene> photoScenes, AiRecap aiRecap) {
        // Query Service가 조합한 스냅샷을 상세 API 계약으로 변환한다.
        public static Data from(MonthlyReportDetail detail) {
            var report = detail.report();
            var place = report.getTopPlace() == null ? null
                    : new TopPlace(report.getTopPlace().getPlaceId(), report.getTopPlace().getLegalDongName());
            var mood = detail.moodStat() == null ? null : detail.moodStat().getAverageMoodScore();
            var scenes = detail.photoScenes().stream()
                    .map(scene -> new PhotoScene(scene.getSceneTag(), scene.getCount(), scene.getRatio().intValue()))
                    .toList();
            String recapText = report.getAiRecapStatus().name().equals("COMPLETED")
                    ? report.getAiRecapText() : null;
            return new Data(report.getMonthlyReportId(), report.getReportYear(), report.getReportMonth(),
                    new Stats(report.getRecordCount(), place, report.getTopArtistName(), mood), scenes,
                    new AiRecap(report.getAiRecapStatus().name(), recapText));
        }
    }
    public record Stats(int recordCount, TopPlace topPlace, String topArtistName,
            java.math.BigDecimal averageMoodScore) { }
    public record TopPlace(Long placeId, String legalDongName) { }
    public record PhotoScene(String sceneTag, int count, int ratio) { }
    public record AiRecap(String status, String text) { }
}
