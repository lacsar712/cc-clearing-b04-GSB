package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.ObligationStatus;
import com.clearing.netting.domain.model.TradeObligation;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.NetPositionRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.port.out.ObligationRepositoryPort;
import com.clearing.netting.domain.service.MultilateralNettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NettingApplicationService {

    private final NettingRunRepositoryPort runRepository;
    private final ObligationRepositoryPort obligationRepository;
    private final MemberRepositoryPort memberRepository;
    private final NetPositionRepositoryPort positionRepository;
    private final NettingRunStatusService statusService;
    private final MultilateralNettingService nettingService;

    public NettingApplicationService(
            NettingRunRepositoryPort runRepository,
            ObligationRepositoryPort obligationRepository,
            MemberRepositoryPort memberRepository,
            NetPositionRepositoryPort positionRepository,
            NettingRunStatusService statusService) {
        this.runRepository = runRepository;
        this.obligationRepository = obligationRepository;
        this.memberRepository = memberRepository;
        this.positionRepository = positionRepository;
        this.statusService = statusService;
        this.nettingService = new MultilateralNettingService();
    }

    @Transactional(readOnly = true)
    public List<NettingRun> listRuns() {
        return runRepository.findAllOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public NettingRun getRun(String runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new DomainException("RUN_NOT_FOUND", "netting run not found: " + runId));
    }

    @Transactional(readOnly = true)
    public List<NetPosition> getPositions(String runId) {
        getRun(runId);
        return positionRepository.findByRunId(runId);
    }

    @Transactional(readOnly = true)
    public List<TradeObligation> getRunObligations(String runId) {
        getRun(runId);
        return obligationRepository.findByNettingRunId(runId);
    }

    @Transactional
    public NettingRunResult execute(LocalDate settleDate, String currency) {
        if (settleDate == null) {
            throw new DomainException("INVALID_DATE", "settleDate is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new DomainException("INVALID_CURRENCY", "currency is required");
        }
        String ccy = currency.trim().toUpperCase();

        NettingRun run = NettingRun.create(settleDate, ccy);
        run.markRunning();
        run = statusService.saveInNewTx(run);

        try {
            List<TradeObligation> opens = obligationRepository.findOpenBySettleDateAndCurrency(settleDate, ccy);
            Set<String> memberIds = new HashSet<>();
            for (TradeObligation o : opens) {
                memberIds.add(o.getPayerMemberId());
                memberIds.add(o.getPayeeMemberId());
            }
            Map<String, Member> members = new HashMap<>();
            for (Member m : memberRepository.findByIds(memberIds)) {
                members.put(m.getMemberId(), m);
            }

            List<NetPosition> positions = nettingService.net(run.getRunId(), ccy, opens, members);

            for (TradeObligation o : opens) {
                o.markNetted(run.getRunId());
            }
            obligationRepository.saveAll(opens);
            positionRepository.saveAll(positions);

            run.markCompleted();
            run = runRepository.save(run);
            return new NettingRunResult(run, positions, opens);
        } catch (DomainException ex) {
            run.markFailed(ex.getMessage());
            statusService.saveInNewTx(run);
            throw ex;
        } catch (RuntimeException ex) {
            run.markFailed(ex.getMessage() == null ? "unexpected error" : ex.getMessage());
            statusService.saveInNewTx(run);
            throw new DomainException("NETTING_FAILED", ex.getMessage());
        }
    }

    @Transactional
    public NettingRun settle(String runId, String remark, String operator) {
        if (remark == null || remark.isBlank()) {
            throw new DomainException("REMARK_REQUIRED", "settle remark is required");
        }
        NettingRun run = getRun(runId);
        // State guard on the aggregate first: only COMPLETED runs can be settled and
        // a non-blank remark is mandatory. FAILED/RUNNING/SETTLED are rejected here.
        run.markSettled(remark, operator);
        List<TradeObligation> obligations = obligationRepository.findByNettingRunId(runId);
        if (obligations.isEmpty()) {
            throw new DomainException("NO_OBLIGATIONS", "no obligations linked to run");
        }
        for (TradeObligation o : obligations) {
            if (o.getStatus() == ObligationStatus.NETTED) {
                o.markSettled();
            } else if (o.getStatus() != ObligationStatus.SETTLED) {
                throw new DomainException("INVALID_STATE", "obligation not NETTED: " + o.getObligationId());
            }
        }
        obligationRepository.saveAll(obligations);
        return runRepository.save(run);
    }

    public record NettingRunResult(NettingRun run, List<NetPosition> positions, List<TradeObligation> obligations) {
    }
}
