package com.sacco.mvp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoanProductWorkflowService {
    private final ObjectMapper objectMapper;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final SaccoSettingsRepository saccoSettingsRepository;

    public WorkflowDefinition resolveForProduct(String saccoId, LoanProductSetting product) {
        SaccoSettings settings = saccoId == null ? null : saccoSettingsRepository.findById(saccoId).orElse(null);
        if (product == null) {
            return fallbackDefinition(settings);
        }

        boolean managerReviewRequired = product.isManagerReviewRequired();
        boolean loanOfficerReviewRequired = product.getLoanOfficerReviewRequired() != null
            ? Boolean.TRUE.equals(product.getLoanOfficerReviewRequired())
            : settings != null && settings.isLoanOfficerReviewRequired();
        boolean committeeReviewRequired = product.isCommitteeReviewRequired();
        boolean accountantReviewRequired = product.isAccountantReviewRequired();
        boolean disbursementOfficerRequired = product.isDisbursementOfficerRequired();
        int managerPriority = product.getResolvedManagerPriority();
        int loanOfficerPriority = product.getResolvedLoanOfficerPriority();
        ApprovalWorkflowStage startStage = resolveStartStage(
            product.getResolvedWorkflowStartStage(),
            managerReviewRequired,
            managerPriority,
            loanOfficerReviewRequired,
            loanOfficerPriority
        );
        int committeePriority = product.getResolvedCommitteePriority();
        int accountantPriority = product.getResolvedAccountantPriority();
        if (committeeReviewRequired && accountantReviewRequired && committeePriority == accountantPriority) {
            accountantPriority = committeePriority == 3 ? 4 : 3;
        }

        return new WorkflowDefinition(
            orderedStages(managerReviewRequired, managerPriority, loanOfficerReviewRequired, loanOfficerPriority, committeeReviewRequired,
                committeePriority, accountantReviewRequired, accountantPriority),
            startStage,
            managerReviewRequired,
            managerPriority,
            loanOfficerReviewRequired,
            loanOfficerPriority,
            committeeReviewRequired,
            committeePriority,
            committeeReviewRequired ? product.getResolvedCommitteeMinimumVotes() : 0,
            committeeReviewRequired ? product.getResolvedCommitteeApprovalThreshold() : 0,
            accountantReviewRequired,
            accountantPriority,
            disbursementOfficerRequired
        );
    }

    public WorkflowDefinition resolveForApplication(LoanApplication app) {
        if (app == null) {
            return fallbackDefinition(null);
        }
        WorkflowDefinition snapshotDefinition = fromPolicySnapshot(app.getPolicySnapshot());
        if (snapshotDefinition != null) {
            return snapshotDefinition;
        }
        LoanProductSetting product = loanProductSettingRepository.findBySaccoIdAndLoanType(app.getSaccoId(), app.getLoanType())
            .orElse(null);
        return resolveForProduct(app.getSaccoId(), product);
    }

    public Map<String, Object> snapshotData(WorkflowDefinition definition) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (definition == null) {
            return data;
        }
        data.put("approvalFlow", definition.stages().stream().map(Enum::name).toList());
        data.put("workflowStartStage", definition.startStage().name());
        data.put("managerReviewRequired", definition.managerReviewRequired());
        data.put("managerPriority", definition.managerPriority());
        data.put("loanOfficerReviewRequired", definition.loanOfficerReviewRequired());
        data.put("loanOfficerPriority", definition.loanOfficerPriority());
        data.put("committeeReviewRequired", definition.committeeReviewRequired());
        data.put("committeePriority", definition.committeePriority());
        data.put("committeeMinimumVotes", definition.committeeMinimumVotes());
        data.put("committeeApprovalThreshold", definition.committeeApprovalThreshold());
        data.put("accountantReviewRequired", definition.accountantReviewRequired());
        data.put("accountantPriority", definition.accountantPriority());
        data.put("disbursementOfficerRequired", definition.disbursementOfficerRequired());
        return data;
    }

    private WorkflowDefinition fromPolicySnapshot(String policySnapshot) {
        if (policySnapshot == null || policySnapshot.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> data = objectMapper.readValue(policySnapshot, new TypeReference<Map<String, Object>>() {});
            Object approvalFlow = data.get("approvalFlow");
            if (!(approvalFlow instanceof List<?> rawStages) || rawStages.isEmpty()) {
                return null;
            }
            List<ApprovalWorkflowStage> stages = new ArrayList<>();
            for (Object rawStage : rawStages) {
                if (rawStage != null) {
                    stages.add(ApprovalWorkflowStage.valueOf(String.valueOf(rawStage)));
                }
            }
            if (stages.isEmpty()) {
                return null;
            }
            ApprovalWorkflowStage snapshotStartStage = ApprovalWorkflowStage.valueOf(String.valueOf(
                data.getOrDefault("workflowStartStage", ApprovalWorkflowStage.MANAGER.name())
            ));
            boolean managerReviewRequired = booleanValue(data.getOrDefault("managerReviewRequired", true));
            int managerPriority = intValue(data.get("managerPriority"), snapshotStartStage == ApprovalWorkflowStage.LOAN_OFFICER ? 2 : 1);
            boolean loanOfficerReviewRequired = booleanValue(data.get("loanOfficerReviewRequired"));
            int loanOfficerPriority = intValue(data.get("loanOfficerPriority"), snapshotStartStage == ApprovalWorkflowStage.LOAN_OFFICER ? 1 : 2);
            ApprovalWorkflowStage startStage = resolveStartStage(
                snapshotStartStage,
                managerReviewRequired,
                managerPriority,
                loanOfficerReviewRequired,
                loanOfficerPriority
            );
            return new WorkflowDefinition(
                List.copyOf(stages),
                startStage,
                managerReviewRequired,
                managerPriority,
                loanOfficerReviewRequired,
                loanOfficerPriority,
                booleanValue(data.getOrDefault("committeeReviewRequired", false)),
                intValue(data.get("committeePriority"), 3),
                intValue(data.get("committeeMinimumVotes"), 0),
                intValue(data.get("committeeApprovalThreshold"), 0),
                booleanValue(data.getOrDefault("accountantReviewRequired", true)),
                intValue(data.get("accountantPriority"), 4),
                booleanValue(data.getOrDefault("disbursementOfficerRequired", true))
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private WorkflowDefinition fallbackDefinition(SaccoSettings settings) {
        boolean loanOfficerReviewRequired = settings != null && settings.isLoanOfficerReviewRequired();
        boolean committeeReviewRequired = settings == null || settings.isBoardReviewRequired();
        int committeeMinimumVotes = settings == null || settings.getBoardQuorum() == null
            ? 1
            : Math.max(settings.getBoardQuorum(), 1);
        return new WorkflowDefinition(
            orderedStages(true, 1, loanOfficerReviewRequired, 2, committeeReviewRequired, 3, true, 4),
            ApprovalWorkflowStage.MANAGER,
            true,
            1,
            loanOfficerReviewRequired,
            2,
            committeeReviewRequired,
            3,
            committeeReviewRequired ? committeeMinimumVotes : 0,
            committeeReviewRequired ? committeeMinimumVotes : 0,
            true,
            4,
            true
        );
    }

    private List<ApprovalWorkflowStage> orderedStages(boolean managerReviewRequired,
                                                      int managerPriority,
                                                      boolean loanOfficerReviewRequired,
                                                      int loanOfficerPriority,
                                                      boolean committeeReviewRequired,
                                                      int committeePriority,
                                                      boolean accountantReviewRequired,
                                                      int accountantPriority) {
        List<StagePriority> stages = new ArrayList<>();
        if (managerReviewRequired) {
            stages.add(new StagePriority(managerPriority, ApprovalWorkflowStage.MANAGER));
        }
        if (loanOfficerReviewRequired) {
            stages.add(new StagePriority(loanOfficerPriority, ApprovalWorkflowStage.LOAN_OFFICER));
        }
        if (committeeReviewRequired) {
            stages.add(new StagePriority(committeePriority, ApprovalWorkflowStage.BOARD));
        }
        if (accountantReviewRequired) {
            stages.add(new StagePriority(accountantPriority, ApprovalWorkflowStage.ACCOUNTANT));
        }
        stages.add(new StagePriority(5, ApprovalWorkflowStage.DISBURSEMENT_OFFICER));
        return stages.stream()
            .sorted(Comparator.comparingInt(StagePriority::priority))
            .map(StagePriority::stage)
            .toList();
    }

    private ApprovalWorkflowStage resolveStartStage(ApprovalWorkflowStage startStage,
                                                    boolean managerReviewRequired,
                                                    int managerPriority,
                                                    boolean loanOfficerReviewRequired,
                                                    int loanOfficerPriority) {
        if (!managerReviewRequired && loanOfficerReviewRequired) {
            return ApprovalWorkflowStage.LOAN_OFFICER;
        }
        if (!loanOfficerReviewRequired) {
            return ApprovalWorkflowStage.MANAGER;
        }
        if (managerReviewRequired && loanOfficerPriority != managerPriority) {
            return loanOfficerPriority < managerPriority
                ? ApprovalWorkflowStage.LOAN_OFFICER
                : ApprovalWorkflowStage.MANAGER;
        }
        return startStage == ApprovalWorkflowStage.LOAN_OFFICER
            ? ApprovalWorkflowStage.LOAN_OFFICER
            : ApprovalWorkflowStage.MANAGER;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool
            ? bool
            : value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private record StagePriority(int priority, ApprovalWorkflowStage stage) {
    }

    public record WorkflowDefinition(
        List<ApprovalWorkflowStage> stages,
        ApprovalWorkflowStage startStage,
        boolean managerReviewRequired,
        int managerPriority,
        boolean loanOfficerReviewRequired,
        int loanOfficerPriority,
        boolean committeeReviewRequired,
        int committeePriority,
        int committeeMinimumVotes,
        int committeeApprovalThreshold,
        boolean accountantReviewRequired,
        int accountantPriority,
        boolean disbursementOfficerRequired
    ) {
        public WorkflowDefinition(List<ApprovalWorkflowStage> stages,
                                  ApprovalWorkflowStage startStage,
                                  boolean managerReviewRequired,
                                  boolean loanOfficerReviewRequired,
                                  boolean committeeReviewRequired,
                                  int committeePriority,
                                  int committeeMinimumVotes,
                                  int committeeApprovalThreshold,
                                  boolean accountantReviewRequired,
                                  int accountantPriority) {
            this(
                stages,
                startStage,
                managerReviewRequired,
                startStage == ApprovalWorkflowStage.LOAN_OFFICER ? 2 : 1,
                loanOfficerReviewRequired,
                startStage == ApprovalWorkflowStage.LOAN_OFFICER ? 1 : 2,
                committeeReviewRequired,
                committeePriority,
                committeeMinimumVotes,
                committeeApprovalThreshold,
                accountantReviewRequired,
                accountantPriority,
                true
            );
        }

        public WorkflowDefinition(List<ApprovalWorkflowStage> stages,
                                  ApprovalWorkflowStage startStage,
                                  boolean managerReviewRequired,
                                  int managerPriority,
                                  boolean loanOfficerReviewRequired,
                                  int loanOfficerPriority,
                                  boolean committeeReviewRequired,
                                  int committeePriority,
                                  int committeeMinimumVotes,
                                  int committeeApprovalThreshold,
                                  boolean accountantReviewRequired,
                                  int accountantPriority) {
            this(
                stages,
                startStage,
                managerReviewRequired,
                managerPriority,
                loanOfficerReviewRequired,
                loanOfficerPriority,
                committeeReviewRequired,
                committeePriority,
                committeeMinimumVotes,
                committeeApprovalThreshold,
                accountantReviewRequired,
                accountantPriority,
                true
            );
        }
    }
}
