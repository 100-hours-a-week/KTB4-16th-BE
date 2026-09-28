package com.ktb4.team16.mulo.record.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 반경 판정 전 DB 후보 자물쇠의 음악·좌표·생성 시각을 전달한다.
public record NearbyTrackCandidateDto(Long musicTrackId, String title, String artistName,
        BigDecimal latitude, BigDecimal longitude, LocalDateTime createdAt) { }
