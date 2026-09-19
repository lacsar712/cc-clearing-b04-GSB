package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.NettingRunStatus;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.NetPositionRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NettingApplicationServiceSettleTest {

    @Mock
    private NettingRunRepositoryPort runRepository;
    @Mock
    private ObligationRepositoryPort obligationRepository;
    @Mock
    private MemberRepositoryPort memberRepository;
    @Mock
    private NetPositionRepositoryPort positionRepository;
    @Mock
    private NettingRunStatusService statusService;

    private NettingApplicationService service;

    private static final LocalDate D = LocalDate.of(2026, 9, 10);

    @BeforeEach
    void setUp() {
        service = new NettingApplicationService(
                runRepository, obligationRepository, memberRepository, positionRepository, statusService);
    }

    private NettingRun run(NettingRunStatus status) {
        NettingRun r = NettingRun.create(D, "USD");
        if (status != NettingRunStatus.CREATED) {
            r.markRunning();
            if (status == NettingRunStatus.COMPLETED) {
                r.markCompleted();
            } else if (status == NettingRunStatus.FAILED) {
                r.markFailed("boom");
            }
        }
        return r;
    }

    private TradeObligation nettedObligation(String runId) {
        TradeObligation o = TradeObligation.open("m1", "m2", "USD", new BigDecimal("100"), D.minusDays(1), D);
        o.markNetted(runId);
        return o;
    }

    @Test
    void settlesCompletedRunWithRemarkAndPersistsAuditFields() {
        NettingRun run = run(NettingRunStatus.COMPLETED);
        String runId = run.getRunId();
        TradeObligation obligation = nettedObligation(runId);
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(obligationRepository.findByNettingRunId(runId)).thenReturn(List.of(obligation));
        when(runRepository.save(any(NettingRun.class))).thenAnswer(inv -> inv.getArgument(0));

        NettingRun result = service.settle(runId, "  batch closed  ", "alice");

        ArgumentCaptor<NettingRun> captor = ArgumentCaptor.forClass(NettingRun.class);
        verify(runRepository).save(captor.capture());
        NettingRun saved = captor.getValue();
        assertEquals(NettingRunStatus.SETTLED, saved.getStatus());
        assertEquals("batch closed", saved.getSettleRemark());
        assertEquals("alice", saved.getSettledBy());
        assertEquals(NettingRunStatus.SETTLED, result.getStatus());
        assertEquals(ObligationStatus.SETTLED, obligation.getStatus());
    }

    @Test
    void blankRemarkIsRejectedBeforeLoadingRun() {
        DomainException ex = assertThrows(DomainException.class,
                () -> service.settle("run-x", "   ", "alice"));
        assertEquals("REMARK_REQUIRED", ex.getCode());
        verify(runRepository, never()).save(any());
        verify(obligationRepository, never()).saveAll(any());
    }

    @Test
    void failedRunCannotBeSettled() {
        NettingRun run = run(NettingRunStatus.FAILED);
        when(runRepository.findById(run.getRunId())).thenReturn(Optional.of(run));

        DomainException ex = assertThrows(DomainException.class,
                () -> service.settle(run.getRunId(), "remark", "alice"));
        assertEquals("INVALID_STATE", ex.getCode());
        assertTrue(ex.getMessage().contains("FAILED"));
        verify(runRepository, never()).save(any());
        verify(obligationRepository, never()).saveAll(any());
    }

    @Test
    void runningRunCannotBeSettled() {
        NettingRun run = run(NettingRunStatus.RUNNING);
        when(runRepository.findById(run.getRunId())).thenReturn(Optional.of(run));

        DomainException ex = assertThrows(DomainException.class,
                () -> service.settle(run.getRunId(), "remark", "alice"));
        assertEquals("INVALID_STATE", ex.getCode());
        verify(runRepository, never()).save(any());
    }

    @Test
    void alreadySettledRunCannotBeSettledAgain() {
        NettingRun run = run(NettingRunStatus.COMPLETED);
        run.markSettled("first", "alice");
        when(runRepository.findById(run.getRunId())).thenReturn(Optional.of(run));

        DomainException ex = assertThrows(DomainException.class,
                () -> service.settle(run.getRunId(), "second", "bob"));
        assertEquals("ALREADY_SETTLED", ex.getCode());
        verify(runRepository, never()).save(any());
    }

    @Test
    void unknownRunRejectedWithNotFound() {
        when(runRepository.findById("missing")).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class,
                () -> service.settle("missing", "remark", "alice"));
        assertEquals("RUN_NOT_FOUND", ex.getCode());
    }
}
