package com.ktb4.team16.mulo.memorysearch.service;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.memorysearch.exception.MemorySearchDomainException;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 활성 본인 자물쇠가 없는 사용자의 AI 질문을 막는다. */
@Component
@RequiredArgsConstructor
public class MemorySearchRecordAvailability {

    private final RecordRepository recordRepository;

    /** 인증 사용자에게 활성 자물쇠가 없으면 공개 400 오류를 던진다. */
    public void requireAnyOwnedRecord(Long userId) {
        if (!recordRepository.existsByUser_UserIdAndUser_DeletedAtIsNullAndDeletedAtIsNull(
                userId)) {
            throw new MemorySearchDomainException(ErrorCode.MEMORY_SEARCH_NO_RECORDS);
        }
    }
}
