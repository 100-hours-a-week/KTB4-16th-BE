package com.ktb4.team16.mulo.report.batch;

import com.ktb4.team16.mulo.report.dto.request.MonthlyReportAiCallbackRequest.Result;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

/** AI 작업 ID·생성시각·배열 순서와 무관하게 결과 내용의 동일성을 판별한다. */
public final class ReportCallbackFingerprint {
    /** 유틸리티는 인스턴스 상태를 갖지 않는다. */
    private ReportCallbackFingerprint() { }

    /** 문자열 길이를 포함해 구분자 충돌 없이 태그별 결과를 정규화하고 SHA-256을 계산한다. */
    public static String of(Result result) {
        StringBuilder value = new StringBuilder(result.status().name());
        if (result.aiRecap() != null) {
            String text = result.aiRecap().text();
            value.append(':').append(text.length()).append(':').append(text);
        }
        if (result.photoScenes() != null) {
            result.photoScenes().stream().sorted(Comparator.comparing(scene -> scene.tag())).forEach(scene ->
                    value.append(':').append(scene.tag().length()).append(':').append(scene.tag())
                            .append(':').append(scene.count()).append(':').append(scene.ratio()));
        }
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
