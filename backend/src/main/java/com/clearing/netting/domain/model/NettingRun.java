package com.clearing.netting.domain.model;

import com.clearing.netting.domain.exception.DomainException;

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
    private String settleRemark;
    private Instant settledAt;
    private String settledBy;

    public NettingRun(
            String runId,
            LocalDate settleDate,
            String currency,
            NettingRunStatus status,
            Instant createdAt,
            String failureReason) {
        this(runId, settleDate, currency, status, createdAt, failureReason, null, null, null);
    }

    public NettingRun(
            String runId,
            LocalDate settleDate,
            String currency,
            NettingRunStatus status,
            Instant createdAt,
            String failureReason,
            String settleRemark,
            Instant settledAt,
            String settledBy) {
        this.runId = Objects.requireNonNull(runId);
        this.settleDate = Objects.requireNonNull(settleDate);
        this.currency = Objects.requireNonNull(currency).toUpperCase();
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.failureReason = failureReason;
        this.settleRemark = settleRemark;
        this.settledAt = settledAt;
        this.settledBy = settledBy;
    }

    public static NettingRun create(LocalDate settleDate, String currency) {
        return new NettingRun(
                UUID.randomUUID().toString(),
                settleDate,
                currency,
                NettingRunStatus.CREATED,
                Instant.now(),
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

    /**
     * Settle a completed run. Only COMPLETED runs can be settled and a non-blank
     * remark plus the settling operator are required for an auditable close-out.
     */
    public void markSettled(String remark, String operator) {
        if (remark == null || remark.isBlank()) {
            throw new DomainException(
                    "REMARK_REQUIRED", "settle remark is required");
        }
        if (this.status == NettingRunStatus.SETTLED) {
            throw new DomainException(
                    "ALREADY_SETTLED", "run is already SETTLED and cannot be settled again");
        }
        if (this.status == NettingRunStatus.FAILED) {
            throw new DomainException(
                    "INVALID_STATE", "FAILED runs cannot be settled");
        }
        if (this.status == NettingRunStatus.RUNNING || this.status == NettingRunStatus.CREATED) {
            throw new DomainException(
                    "INVALID_STATE", "only COMPLETED runs can be settled; run is still " + this.status);
        }
        this.status = NettingRunStatus.SETTLED;
        this.settleRemark = remark.trim();
        this.settledAt = Instant.now();
        this.settledBy = Objects.requireNonNull(operator, "operator");
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

    public String getSettleRemark() {
        return settleRemark;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public String getSettledBy() {
        return settledBy;
    }
}
