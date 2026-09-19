package com.clearing.netting.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NettingRunTest {

    @Test
    void settleFromCompletedRecordsOperatorNoteAndTime() {
        NettingRun run = NettingRun.create(LocalDate.of(2026, 9, 19), "USD");
        run.markRunning();
        run.markCompleted();

        run.markSettled("operator", "daily settlement confirmed");

        assertEquals(NettingRunStatus.SETTLED, run.getStatus());
        assertEquals("operator", run.getSettledBy());
        assertEquals("daily settlement confirmed", run.getSettleNote());
        assertNotNull(run.getSettledAt());
    }

    @Test
    void settleRejectedWhenNotCompleted() {
        NettingRun running = NettingRun.create(LocalDate.of(2026, 9, 19), "USD");
        running.markRunning();
        assertThrows(IllegalStateException.class, () -> running.markSettled("operator", "note"));

        NettingRun failed = NettingRun.create(LocalDate.of(2026, 9, 19), "USD");
        failed.markRunning();
        failed.markFailed("boom");
        assertThrows(IllegalStateException.class, () -> failed.markSettled("operator", "note"));

        NettingRun settled = NettingRun.create(LocalDate.of(2026, 9, 19), "USD");
        settled.markRunning();
        settled.markCompleted();
        settled.markSettled("operator", "note");
        assertThrows(IllegalStateException.class, () -> settled.markSettled("operator", "again"));
    }
}
