package com.sacco.mvp.service;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanProductBoardReviewer;
import com.sacco.mvp.domain.LoanProductRequiredAttachment;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanProductStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.SaccoSettings;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.domain.SaccoStationPolicy;
import com.sacco.mvp.repository.LoanProductBoardReviewerRepository;
import com.sacco.mvp.repository.LoanProductRequiredAttachmentRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.SaccoSettingsRepository;
import com.sacco.mvp.repository.SaccoStationPolicyRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ChairpersonConfigurationService {
    public static final int PAGE_SIZE = 25;

    private final SaccoSettingsRepository saccoSettingsRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final SaccoStationPolicyRepository saccoStationPolicyRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final LoanProductBoardReviewerRepository reviewerRepository;
    private final LoanProductRequiredAttachmentRepository attachmentRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public ConfigurationPage overview(AppUserPrincipal principal, String search, int requestedPage) {
        Scope scope = scope(principal);
        SaccoSettings settings = saccoSettingsRepository.findById(scope.saccoId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        SaccoStation station = saccoStationRepository.findBySaccoIdAndStationId(scope.saccoId(), scope.stationId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        SaccoStationPolicy stationPolicy = saccoStationPolicyRepository
            .findBySaccoIdAndStationId(scope.saccoId(), scope.stationId()).orElse(null);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Page<LoanProductSetting> products = loanProductSettingRepository.findConfigurationPage(
            scope.saccoId(), Set.of(LoanProductStatus.DRAFT, LoanProductStatus.RETIRED), normalizedSearch,
            PageRequest.of(Math.max(0, requestedPage), PAGE_SIZE));

        List<ProductRow> productRows = products.getContent().stream().map(product -> new ProductRow(
            product.getId(), product.getDisplayCode(), product.getDisplayName(), product.getStatus().name(),
            product.getMinimumAmount(), product.getMaximumAmount(), workflowStages(product))).toList();

        EffectiveValue<Boolean> guarantorActiveLoanAllowed = effective(
            stationPolicy == null ? null : stationPolicy.getGuarantorWithActiveLoanAllowed(),
            settings.getGuarantorWithActiveLoanAllowed());
        EffectivePolicies policies = new EffectivePolicies(
            effective(stationPolicy == null ? null : stationPolicy.getApplicantMaxDefaultedLoans(), settings.getApplicantMaxDefaultedLoans()),
            new EffectiveValue<>(guarantorActiveLoanAllowed.value() == null || guarantorActiveLoanAllowed.value(),
                guarantorActiveLoanAllowed.sourceCode()),
            effective(stationPolicy == null ? null : stationPolicy.getGuarantorMaxGuaranteedLoanAmount(), settings.getGuarantorMaxGuaranteedLoanAmount()),
            effective(stationPolicy == null ? null : stationPolicy.getGuarantorMaxDefaultedLoans(), settings.getGuarantorMaxDefaultedLoans()));
        OtpConfiguration otp = new OtpConfiguration(station.getResolvedOtpDeliveryChannel().name(),
            station.getResolvedOtpRequirementMode().name());

        return new ConfigurationPage(scope.stationId(), policies, otp, productRows, normalizedSearch,
            products.getNumber(), products.getTotalPages(), products.getTotalElements(), products.isFirst(), products.isLast());
    }

    @Transactional(readOnly = true)
    public ProductDetail product(AppUserPrincipal principal, UUID productId) {
        Scope scope = scope(principal);
        SaccoSettings settings = saccoSettingsRepository.findById(scope.saccoId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        LoanProductSetting product = loanProductSettingRepository.findByIdAndSaccoId(productId, scope.saccoId())
            .filter(value -> value.getStatus() == LoanProductStatus.ACTIVE
                || value.getStatus() == LoanProductStatus.SUSPENDED)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));

        List<LoanProductBoardReviewer> assignments = reviewerRepository
            .findByLoanProductSettingIdOrderByCreatedAtAsc(productId);
        Map<UUID, Member> members = memberRepository.findAllById(assignments.stream()
                .map(LoanProductBoardReviewer::getBoardMemberId).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        List<ReviewerView> reviewers = assignments.stream().map(assignment -> {
            Member member = members.get(assignment.getBoardMemberId());
            return new ReviewerView(assignment.getReviewStage().name(), assignment.getReviewStage().getDisplayLabel(),
                member == null ? "-" : member.getFullName(), member == null ? "-" : member.getStaffNo());
        }).toList();
        List<AttachmentRule> attachments = attachmentRepository
            .findByLoanProductSettingIdAndActiveTrueOrderByDisplayOrderAscCreatedAtAsc(productId).stream()
            .map(value -> new AttachmentRule(value.getAttachmentName(), value.getMaxSizeMb())).toList();

        return new ProductDetail(product.getId(), product.getDisplayCode(), product.getDisplayName(),
            product.getDisplayDescription(), product.getStatus().name(), product.getLoanType().name(),
            product.getMinimumAmount(), product.getMaximumAmount(), product.getInterestRate(),
            product.getInterestMethod() == null ? "-" : product.getInterestMethod().name(),
            product.getMinimumRepaymentMonths(), product.getMaxRepaymentMonths(), product.getGuarantorsRequired(),
            product.getMaxLoanSavingsRatio(), product.getResolvedApplicationFee(settings.getResolvedApplicationFee()),
            product.getResolvedProcessingFeeRate(), product.getInsuranceRate(),
            product.isSavingsLimitCheckRequired(), product.isApplicationWithActiveLoanAllowed(),
            product.isFreshFinancialDataRequired(), product.isApplicantAttachmentRequired(),
            product.isGuarantorMinSavingsCheckRequired(), product.getResolvedGuarantorMinimumSavings(),
            product.isCommitteeReviewRequired(), product.getResolvedCommitteeMinimumVotes(), product.getResolvedCommitteeApprovalThreshold(),
            product.isDisbursementProofRequired(), workflowStages(product), attachments, reviewers);
    }

    private List<WorkflowStageView> workflowStages(LoanProductSetting product) {
        List<WorkflowStageView> stages = new ArrayList<>();
        if (product.isManagerReviewRequired()) stages.add(stage(ApprovalWorkflowStage.MANAGER, product.getResolvedManagerPriority()));
        if (Boolean.TRUE.equals(product.getLoanOfficerReviewRequired())) stages.add(stage(ApprovalWorkflowStage.LOAN_OFFICER, product.getResolvedLoanOfficerPriority()));
        if (product.isChairpersonReviewRequired()) stages.add(stage(ApprovalWorkflowStage.CHAIRPERSON, product.getResolvedChairpersonPriority()));
        if (product.isBoardReviewRequired()) stages.add(stage(ApprovalWorkflowStage.BOARD, product.getResolvedBoardPriority()));
        if (product.isCommitteeReviewRequired()) stages.add(stage(ApprovalWorkflowStage.CREDIT_COMMITTEE, product.getResolvedCommitteePriority()));
        if (product.isAccountantReviewRequired()) stages.add(stage(ApprovalWorkflowStage.ACCOUNTANT, product.getResolvedAccountantPriority()));
        if (product.isDisbursementOfficerRequired()) stages.add(stage(ApprovalWorkflowStage.DISBURSEMENT_OFFICER, 7));
        return stages.stream().sorted(Comparator.comparingInt(WorkflowStageView::priority)
            .thenComparing(WorkflowStageView::label)).toList();
    }

    private WorkflowStageView stage(ApprovalWorkflowStage stage, int priority) {
        return new WorkflowStageView(priority, stage.name(), stage.getDisplayLabel());
    }

    private <T> EffectiveValue<T> effective(T override, T fallback) {
        return override == null ? new EffectiveValue<>(fallback, "saccoDefault")
            : new EffectiveValue<>(override, "stationOverride");
    }

    private Scope scope(AppUserPrincipal principal) {
        if (principal == null || principal.getSaccoId() == null || principal.getSaccoId().isBlank()
            || principal.getStationId() == null || principal.getStationId().isBlank()) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        return new Scope(principal.getSaccoId(), principal.getStationId());
    }

    private record Scope(String saccoId, String stationId) {}

    public record ConfigurationPage(String stationId, EffectivePolicies policies,
                                    OtpConfiguration otp, List<ProductRow> products, String search,
                                    int page, int totalPages, long totalElements, boolean first, boolean last) {}
    public record EffectivePolicies(EffectiveValue<Integer> applicantMaxDefaults,
                                    EffectiveValue<Boolean> guarantorActiveLoanAllowed,
                                    EffectiveValue<BigDecimal> guarantorMaximumActiveGuarantees,
                                    EffectiveValue<Integer> guarantorMaxDefaults) {}
    public record EffectiveValue<T>(T value, String sourceCode) {}
    public record OtpConfiguration(String deliveryChannel, String requirementMode) {}
    public record ProductRow(UUID id, String code, String name, String status,
                             BigDecimal minimumAmount, BigDecimal maximumAmount, List<WorkflowStageView> workflow) {}
    public record ProductDetail(UUID id, String code, String name, String description, String status, String loanType,
                                BigDecimal minimumAmount, BigDecimal maximumAmount, BigDecimal interestRate,
                                String interestMethod, Integer minimumMonths, Integer maximumMonths,
                                Integer guarantorsRequired, BigDecimal maximumSavingsRatio, BigDecimal applicationFee,
                                BigDecimal processingFeeRate, BigDecimal insuranceRate, boolean savingsCheckRequired,
                                boolean activeLoanAllowed, boolean freshFinancialDataRequired,
                                boolean applicantAttachmentRequired, boolean guarantorSavingsCheckRequired,
                                BigDecimal guarantorMinimumSavings, boolean committeeReviewRequired,
                                int minimumVotes, int approvalThreshold,
                                boolean disbursementProofRequired, List<WorkflowStageView> workflow,
                                List<AttachmentRule> attachments, List<ReviewerView> reviewers) {}
    public record WorkflowStageView(int priority, String stage, String label) {}
    public record AttachmentRule(String name, BigDecimal maxSizeMb) {}
    public record ReviewerView(String stage, String stageLabel, String name, String staffNumber) {}
}
