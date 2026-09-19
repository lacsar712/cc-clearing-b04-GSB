package com.clearing.netting.domain.model;

import com.clearing.netting.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NettingRunTest {

    private NettingRun run(NettingRunStatus status) {
        return new NettingRun(
                "run-1",
                LocalDate.of(2026, 9, 10),
                "USD",
                status,
                Instant.parse("2026-09-10T08:00:00Z"),
                null);
    }

    @Test
    void completedRunSettlesWithRemarkAndAuditFields() {
        NettingRun run = run(NettingRunStatus.COMPLETED);

        run.markSettled("  end-of-day batch verified  ", "operator");

        assertEquals(NettingRunStatus.SETTLED, run.getStatus());
        assertEquals("end-of-day batch verified", run.getSettleRemark());
        assertEquals("operator", run.getSettledBy());
        assertNotNull(run.getSettledAt());
    }

    @Test
    void blankRemarkIsRejected() {
        NettingRun run = run(NettingRunStatus.COMPLETED);

        DomainException ex = assertThrows(DomainException.class,
                () -> run.markSettled("   ", "operator"));
        assertEquals("REMARK_REQUIRED", ex.getCode());
        // run must remain COMPLETED after a rejected settle
        assertEquals(NettingRunStatus.COMPLETED, run.getStatus());
        assertNull(run.getSettledAt());
    }

    @Test
    void failedRunCannotBeSettled() {
        NettingRun run = run(NettingRunStatus.FAILED);

        DomainException ex = assertThrows(DomainException.class,
                () -> run.markSettled("a remark", "operator"));
        assertEquals("INVALID_STATE", ex.getCode());
        assertTrue(ex.getMessage().contains("FAILED"));
        assertEquals(NettingRunStatus.FAILED, run.getStatus());
    }

    @Test
    void runningRunCannotBeSettled() {
        NettingRun run = run(NettingRunStatus.RUNNING);

        DomainException ex = assertThrows(DomainException.class,
                () -> run.markSettled("a remark", "operator"));
        assertEquals("INVALID_STATE", ex.getCode());
        assertTrue(ex.getMessage().toUpperCase().contains("RUNNING"));
        assertEquals(NettingRunStatus.RUNNING, run.getStatus());
    }

    @Test
    void createdRunCannotBeSettled() {
        NettingRun run = run(NettingRunStatus.CREATED);

        DomainException ex = assertThrows(DomainException.class,
                () -> run.markSettled("a remark", "operator"));
        assertEquals("INVALID_STATE", ex.getCode());
        assertEquals(NettingRunStatus.CREATED, run.getStatus());
    }

    @Test
    void alreadySettledRunCannotBeSettledAgain() {
        NettingRun run = run(NettingRunStatus.COMPLETED);
        run.markSettled("first settle", "operator");

        DomainException ex = assertThrows(DomainException.class,
                () -> run.markSettled("second settle", "operator"));
        assertEquals("ALREADY_SETTLED", ex.getCode());
        assertEquals("first settle", run.getSettleRemark());
    }
}
