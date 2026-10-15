package com.ktb4.team16.mulo.memorysearch.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.global.error.ErrorCode;
import com.ktb4.team16.mulo.memorysearch.exception.MemorySearchDomainException;
import com.ktb4.team16.mulo.record.repository.RecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemorySearchRecordAvailabilityTest {

    @Mock
    private RecordRepository recordRepository;

    /** 활성 본인 자물쇠가 하나라도 있으면 검색 질문을 허용한다. */
    @Test
    void allowsQuestionWhenTheUserHasAnActiveRecord() {
        when(recordRepository.existsByUser_UserIdAndUser_DeletedAtIsNullAndDeletedAtIsNull(35L))
                .thenReturn(true);

        assertThatCode(() -> new MemorySearchRecordAvailability(recordRepository)
                .requireAnyOwnedRecord(35L)).doesNotThrowAnyException();
    }

    /** 자물쇠가 0개면 AI 호출 전에 공개 400 오류로 차단한다. */
    @Test
    void rejectsQuestionWhenTheUserHasNoActiveRecords() {
        when(recordRepository.existsByUser_UserIdAndUser_DeletedAtIsNullAndDeletedAtIsNull(35L))
                .thenReturn(false);

        assertThatThrownBy(() -> new MemorySearchRecordAvailability(recordRepository)
                .requireAnyOwnedRecord(35L))
                .isInstanceOfSatisfying(MemorySearchDomainException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.errorCode())
                                .isEqualTo(ErrorCode.MEMORY_SEARCH_NO_RECORDS));
    }
}
