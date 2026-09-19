package com.clearing.netting.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class NettingRun {
    private final String runId;
    private final LocalDate settleDate;
    private final String currency;
    private NettingRunStatus status;
    private final Instant createdAt;
    private String failureReason;
    private Instant settledAt;
    private String settledBy;
    private String settleNote;

    public NettingRun(
            String runId,
            LocalDate settleDate,
            String currency,
            NettingRunStatus status,
            Instant createdAt,
            String failureReason,
            Instant settledAt,
            String settledBy,
            String settleNote) {
        this.runId = Objects.requireNonNull(runId);
        this.settleDate = Objects.requireNonNull(settleDate);
        this.currency = Objects.requireNonNull(currency).toUpperCase();
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.failureReason = failureReason;
        this.settledAt = settledAt;
        this.settledBy = settledBy;
        this.settleNote = settleNote;
    }

    public static NettingRun create(LocalDate settleDate, String currency) {
        return new NettingRun(
                UUID.randomUUID().toString(),
                settleDate,
                currency,
                NettingRunStatus.CREATED,
                Instant.now(),
                null,
                null,
                null,
                null);
    }

    public void markRunning() {
        this.status = NettingRunStatus.RUNNING;
    }

    public void markCompleted() {
        this.status = NettingRunStatus.COMPLETED;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = NettingRunStatus.FAILED;
        this.failureReason = reason;
    }

    public void markSettled(String operator, String note) {
        if (status != NettingRunStatus.COMPLETED) {
            throw new IllegalStateException("only COMPLETED runs can be settled");
        }
        this.status = NettingRunStatus.SETTLED;
        this.settledAt = Instant.now();
        this.settledBy = operator;
        this.settleNote = note;
    }

    public String getRunId() {
        return runId;
    }

    public LocalDate getSettleDate() {
        return settleDate;
    }

    public String getCurrency() {
        return currency;
    }

    public NettingRunStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public String getSettledBy() {
        return settledBy;
    }

    public String getSettleNote() {
        return settleNote;
    }
}
