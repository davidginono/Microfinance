package com.sacco.mvp.service;

import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
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
public class ChairpersonProcessedLoanService {
    public static final int PAGE_SIZE = 50;

    private static final Set<LoanStatus> PROCESSED_STATUSES = processedStatuses();
    private static final List<LoanStatus> PROCESSED_STATUS_OPTIONS = java.util.Arrays.stream(LoanStatus.values())
        .filter(PROCESSED_STATUSES::contains)
        .toList();

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final MemberRepository memberRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final LoanPresentationService loanPresentationService;
    private final ApplicationClock applicationClock;

    @Transactional(readOnly = true)
    public ProcessedLoanPage list(AppUserPrincipal principal, String search, LocalDate fromDate,
                                  LocalDate toDate, LoanStatus status, int requestedPage) {
        Scope scope = scope(principal);
        String normalizedSearch = normalizeSearch(search);
        int pageNumber = Math.max(0, requestedPage);
        OffsetDateTime updatedFrom = applicationClock.startOfDay(fromDate);
        OffsetDateTime updatedToExclusive = applicationClock.dayAfter(toDate);
        Page<LoanApplication> applications = loanApplicationRepository.findProcessedLoansPage(
            scope.saccoId(), scope.stationId(), PROCESSED_STATUSES, normalizedSearch,
            updatedFrom, updatedToExclusive, status,
            PageRequest.of(pageNumber, PAGE_SIZE, Sort.unsorted()));

        Set<UUID> loanIds = applications.getContent().stream().map(LoanApplication::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, List<DecisionView>> decisions = decisionsByLoan(loanIds);
        Map<UUID, Member> applicants = members(applications.getContent().stream()
            .map(LoanApplication::getApplicantMemberId).collect(Collectors.toSet()));
        Map<UUID, LoanProductSetting> products = loanProductSettingRepository.findAllById(
                applications.getContent().stream().map(LoanApplication::getLoanProductSettingId)
                    .filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(LoanProductSetting::getId, Function.identity()));

        List<ProcessedLoanRow> rows = applications.getContent().stream().map(app -> {
            List<DecisionView> loanDecisions = decisions.getOrDefault(app.getId(), List.of());
            DecisionView latest = loanDecisions.isEmpty() ? null : loanDecisions.getFirst();
            Member applicant = applicants.get(app.getApplicantMemberId());
            LoanProductSetting product = products.get(app.getLoanProductSettingId());
            return new ProcessedLoanRow(
                app.getId(), displayApplicationId(app), app.getLoanId(),
                applicant == null ? "-" : applicant.getFullName(),
                applicant == null ? "-" : applicant.getMemberNo(),
                product == null ? app.getLoanType().getDisplayLabel() : product.getDisplayName(),
                app.getAmount(), app.getStatus().name(), latest, loanDecisions.size(), app.getUpdatedAt());
        }).toList();

        return new ProcessedLoanPage(rows, normalizedSearch, fromDate, toDate, status, PROCESSED_STATUS_OPTIONS,
            applications.getNumber(), applications.getTotalPages(), applications.getTotalElements(),
            applications.isFirst(), applications.isLast());
    }

    @Transactional(readOnly = true)
    public ProcessedLoanDetail detail(AppUserPrincipal principal, UUID loanId) {
        Scope scope = scope(principal);
        LoanApplication app = loanApplicationRepository.findProcessedLoan(
                loanId, scope.saccoId(), scope.stationId(), PROCESSED_STATUSES)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));

        List<DecisionView> decisions = decisionsByLoan(Set.of(loanId)).getOrDefault(loanId, List.of());
        var guarantorRequests = guarantorRequestRepository.findByLoanApplicationId(loanId);
        Set<UUID> memberIds = new LinkedHashSet<>();
        memberIds.add(app.getApplicantMemberId());
        guarantorRequests.forEach(request -> memberIds.add(request.getGuarantorMemberId()));
        Map<UUID, Member> memberMap = members(memberIds);
        Member applicant = memberMap.get(app.getApplicantMemberId());
        LoanProductSetting product = app.getLoanProductSettingId() == null ? null
            : loanProductSettingRepository.findByIdAndSaccoId(app.getLoanProductSettingId(), scope.saccoId()).orElse(null);

        List<GuarantorView> guarantors = guarantorRequests.stream().map(request -> {
            Member guarantor = memberMap.get(request.getGuarantorMemberId());
            return new GuarantorView(
                guarantor == null ? "-" : guarantor.getFullName(),
                guarantor == null ? "-" : guarantor.getMemberNo(), request.getStatus().name(),
                request.getRequestedAmount(), request.getCommittedAmount(), request.getDecisionReason(), request.getDecidedAt());
        }).toList();

        return new ProcessedLoanDetail(
            app.getId(), displayApplicationId(app), app.getLoanId(), app.getStatus().name(),
            app.getApplicantMemberId(), applicant == null ? "-" : applicant.getFullName(), applicant == null ? "-" : applicant.getMemberNo(),
            applicant == null ? "-" : applicant.getEmail(), applicant == null ? "-" : applicant.getPhone(),
            product == null ? app.getLoanType().getDisplayLabel() : product.getDisplayName(),
            product == null ? app.getLoanType().defaultProductCode() : product.getDisplayCode(),
            app.getAmount(), app.getTenorMonths(), app.getRequiredGuarantors(), app.getCreatedAt(), app.getSubmittedAt(), app.getUpdatedAt(),
            java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(loanPresentationService.parseFormFields(app.getFormData()))),
            java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(loanPresentationService.parseFinancialFieldSections(app))),
            List.copyOf(loanPresentationService.buildProgressItems(app)),
            List.copyOf(loanPresentationService.parseApplicationAttachments(app.getAttachmentsJson())),
            java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(loanPresentationService.reviewRepaymentSummary(app))),
            app.getDisbursementDate(), app.getFirstRepaymentDate(), app.getFinalDueDate(),
            app.getRepaymentFrequency() == null ? null : app.getRepaymentFrequency().name(), app.getInstallmentAmount(),
            app.getDisbursementReference(), app.getDisbursementNotes(), app.getDepositAmount(), guarantors, decisions);
    }

    public static boolean isProcessedStatus(LoanStatus status) {
        return status != null && PROCESSED_STATUSES.contains(status);
    }

    private Map<UUID, List<DecisionView>> decisionsByLoan(Collection<UUID> loanIds) {
        if (loanIds == null || loanIds.isEmpty()) {
            return Map.of();
        }
        List<ManagerReview> managerReviews = managerReviewRepository.findByLoanApplicationIdInOrderByCreatedAtDesc(loanIds);
        List<BoardReview> boardReviews = boardReviewRepository.findByLoanApplicationIdInOrderByCreatedAtDesc(loanIds);
        Set<UUID> reviewerIds = new LinkedHashSet<>();
        managerReviews.forEach(review -> reviewerIds.add(review.getManagerMemberId()));
        boardReviews.stream().filter(review -> review.getDecision() != BoardDecision.PENDING)
            .forEach(review -> reviewerIds.add(review.getBoardMemberId()));
        Map<UUID, Member> reviewers = members(reviewerIds);

        List<LoanDecision> merged = new ArrayList<>();
        managerReviews.forEach(review -> merged.add(new LoanDecision(review.getLoanApplicationId(), new DecisionView(
            review.getReviewStage().name(), review.getReviewStage().getDisplayLabel(),
            decisionLabel(review), reviewerName(reviewers.get(review.getManagerMemberId())),
            reviewerNumber(reviewers.get(review.getManagerMemberId())), review.getReasons(), review.getCreatedAt()))));
        boardReviews.stream().filter(review -> review.getDecision() != BoardDecision.PENDING).forEach(review ->
            merged.add(new LoanDecision(review.getLoanApplicationId(), new DecisionView(
                review.getReviewStage().name(), review.getReviewStage().getDisplayLabel(),
                decisionLabel(review.getDecision()), reviewerName(reviewers.get(review.getBoardMemberId())),
                reviewerNumber(reviewers.get(review.getBoardMemberId())), review.getComment(),
                review.getDecidedAt() == null ? review.getCreatedAt() : review.getDecidedAt()))));

        return merged.stream().collect(Collectors.groupingBy(LoanDecision::loanId,
            Collectors.mapping(LoanDecision::decision, Collectors.collectingAndThen(Collectors.toList(), values -> values.stream()
                .sorted(Comparator.comparing(DecisionView::decidedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList()))));
    }

    private Map<UUID, Member> members(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findAllById(ids).stream().collect(Collectors.toMap(Member::getId, Function.identity()));
    }

    private Scope scope(AppUserPrincipal principal) {
        if (principal == null || principal.getSaccoId() == null || principal.getSaccoId().isBlank()
            || principal.getStationId() == null || principal.getStationId().isBlank()) {
            throw new ResponseStatusException(NOT_FOUND);
        }
        return new Scope(principal.getSaccoId(), principal.getStationId());
    }

    private String normalizeSearch(String search) {
        return search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    }

    private String displayApplicationId(LoanApplication app) {
        return app.getApplicationNumber() == null ? app.getId().toString() : app.getApplicationNumber().toString();
    }

    private String reviewerName(Member member) {
        return member == null || member.getFullName() == null || member.getFullName().isBlank() ? "-" : member.getFullName();
    }

    private String reviewerNumber(Member member) {
        return member == null || member.getStaffNo() == null || member.getStaffNo().isBlank() ? "-" : member.getStaffNo();
    }

    private String decisionLabel(ManagerReview review) {
        if (review.getDecision() == ManagerDecision.REJECT) {
            return "REJECTED";
        }
        return review.getReviewStage() == com.sacco.mvp.domain.ApprovalWorkflowStage.DISBURSEMENT_OFFICER
            ? "DISBURSED" : "APPROVED";
    }

    private String decisionLabel(BoardDecision decision) {
        return decision == BoardDecision.REJECTED ? "REJECTED" : "APPROVED";
    }

    private static Set<LoanStatus> processedStatuses() {
        EnumSet<LoanStatus> statuses = EnumSet.allOf(LoanStatus.class);
        statuses.removeAll(EnumSet.of(LoanStatus.DRAFT, LoanStatus.SUBMITTED, LoanStatus.AWAITING_GUARANTORS,
            LoanStatus.ALL_GUARANTORS_APPROVED));
        return Set.copyOf(statuses);
    }

    private record Scope(String saccoId, String stationId) {}
    private record LoanDecision(UUID loanId, DecisionView decision) {}

    public record ProcessedLoanPage(List<ProcessedLoanRow> rows, String search, LocalDate fromDate,
                                    LocalDate toDate, LoanStatus status, List<LoanStatus> availableStatuses,
                                    int page, int totalPages, long totalElements, boolean first, boolean last) {}
    public record ProcessedLoanRow(UUID id, String applicationId, String loanId, String applicantName,
                                   String memberNo, String productName, BigDecimal amount, String status,
                                   DecisionView latestDecision, int decisionCount, OffsetDateTime updatedAt) {}
    public record DecisionView(String stage, String stageLabel, String decision, String reviewerName,
                               String reviewerNumber, String note, OffsetDateTime decidedAt) {}
    public record GuarantorView(String name, String memberNo, String status, BigDecimal requestedAmount,
                                BigDecimal committedAmount, String reason, OffsetDateTime decidedAt) {}
    public record ProcessedLoanDetail(UUID id, String applicationId, String loanId, String status,
                                      UUID applicantId, String applicantName, String memberNo, String email, String phone,
                                      String productName, String productCode, BigDecimal amount, Integer tenorMonths,
                                      Integer requiredGuarantors, OffsetDateTime createdAt, OffsetDateTime submittedAt,
                                      OffsetDateTime updatedAt, Map<String, Object> formFields,
                                      Map<String, Map<String, Object>> financialFieldSections,
                                      List<Map<String, Object>> progressItems, List<Map<String, Object>> attachments,
                                      Map<String, Object> repaymentSummary, java.time.LocalDate disbursementDate,
                                      java.time.LocalDate firstRepaymentDate, java.time.LocalDate finalDueDate,
                                      String repaymentFrequency, BigDecimal installmentAmount,
                                      String disbursementReference, String disbursementNotes, BigDecimal depositAmount,
                                      List<GuarantorView> guarantors, List<DecisionView> decisions) {}
}
