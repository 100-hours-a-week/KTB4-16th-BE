package com.ktb4.team16.mulo.dailysong.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.dailysong.repository.DailySongJobCreationRepository;
import com.ktb4.team16.mulo.dailysong.repository.DailySongJobCreationRepository.Claim;
import com.ktb4.team16.mulo.dailysong.repository.DailySongTargetUserRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class DailySongJobCreationServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 12);
    private static final LocalDateTime CUTOFF = DATE.atTime(0, 30);
    private static final Duration LEASE = Duration.ofMinutes(2);

    private final DailySongTargetUserRepository targets = mock(DailySongTargetUserRepository.class);
    private final DailySongJobCreationRepository creations = mock(DailySongJobCreationRepository.class);
    private final DailySongJobCreationService service = new DailySongJobCreationService(targets, creations);

    @Test
    void createsPagesInOrderAndFinalizesWithTotalCount() {
        Claim claim = new Claim(DATE, CUTOFF, "token");
        when(creations.claimRun(DATE, CUTOFF, LEASE)).thenReturn(Optional.of(claim));
        when(targets.findUserIdsAtCutoff(CUTOFF, 0, 100)).thenReturn(List.of(2L, 4L));
        when(targets.findUserIdsAtCutoff(CUTOFF, 4, 100)).thenReturn(List.of(8L));
        when(targets.findUserIdsAtCutoff(CUTOFF, 8, 100)).thenReturn(List.of());
        when(creations.markReady(claim, 3)).thenReturn(true);

        service.createJobs(DATE);

        verify(creations).writeBatch(claim, List.of(2L, 4L), LEASE);
        verify(creations).writeBatch(claim, List.of(8L), LEASE);
        verify(creations).markReady(claim, 3);
    }

    @Test
    void finalizesEmptyTargetList() {
        Claim claim = new Claim(DATE, CUTOFF, "token");
        when(creations.claimRun(DATE, CUTOFF, LEASE)).thenReturn(Optional.of(claim));
        when(targets.findUserIdsAtCutoff(CUTOFF, 0, 100)).thenReturn(List.of());
        when(creations.markReady(claim, 0)).thenReturn(true);

        service.createJobs(DATE);

        verify(creations).markReady(claim, 0);
    }

    @Test
    void doesNotReadUsersWhenRunIsAlreadyClaimedOrReady() {
        when(creations.claimRun(DATE, CUTOFF, LEASE)).thenReturn(Optional.empty());

        service.createJobs(DATE);

        verifyNoInteractions(targets);
    }

    @Test
    void createsMoreUsersThanOnePage() {
        Claim claim = new Claim(DATE, CUTOFF, "token");
        List<Long> firstPage = LongStream.rangeClosed(1, 100).boxed().toList();
        when(creations.claimRun(DATE, CUTOFF, LEASE)).thenReturn(Optional.of(claim));
        when(targets.findUserIdsAtCutoff(CUTOFF, 0, 100)).thenReturn(firstPage);
        when(targets.findUserIdsAtCutoff(CUTOFF, 100, 100)).thenReturn(List.of(101L));
        when(targets.findUserIdsAtCutoff(CUTOFF, 101, 100)).thenReturn(List.of());
        when(creations.markReady(claim, 101)).thenReturn(true);

        service.createJobs(DATE);

        verify(creations).writeBatch(claim, firstPage, LEASE);
        verify(creations).writeBatch(claim, List.of(101L), LEASE);
        verify(creations).markReady(claim, 101);
    }

    @Test
    void batchFailureDoesNotFinalizeRun() {
        Claim claim = new Claim(DATE, CUTOFF, "token");
        when(creations.claimRun(DATE, CUTOFF, LEASE)).thenReturn(Optional.of(claim));
        when(targets.findUserIdsAtCutoff(CUTOFF, 0, 100)).thenReturn(List.of(1L));
        doThrow(new IllegalStateException("테스트 오류")).when(creations).writeBatch(claim, List.of(1L), LEASE);

        assertThatThrownBy(() -> service.createJobs(DATE)).isInstanceOf(IllegalStateException.class);

        verify(creations, never()).markReady(any(), anyLong());
    }
}
