package com.sacco.mvp.service;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChairpersonProcessedLoanServiceTest {
    @Mock LoanApplicationRepository loanApplicationRepository;
    @Mock LoanProductSettingRepository loanProductSettingRepository;
    @Mock ManagerReviewRepository managerReviewRepository;
    @Mock BoardReviewRepository boardReviewRepository;
    @Mock MemberRepository memberRepository;
    @Mock GuarantorRequestRepository guarantorRequestRepository;
    @Mock LoanPresentationService loanPresentationService;

    ChairpersonProcessedLoanService service;

    @BeforeEach
    void setUp() {
        service = new ChairpersonProcessedLoanService(loanApplicationRepository, loanProductSettingRepository,
            managerReviewRepository, boardReviewRepository, memberRepository, guarantorRequestRepository,
            loanPresentationService, new ApplicationClock("Africa/Nairobi"));
    }

    @Test
    void processedStatusBoundaryExcludesOnlyPreReviewApplications() {
        assertThat(LoanStatus.values()).filteredOn(ChairpersonProcessedLoanService::isProcessedStatus)
            .doesNotContain(LoanStatus.DRAFT, LoanStatus.SUBMITTED, LoanStatus.AWAITING_GUARANTORS,
                LoanStatus.ALL_GUARANTORS_APPROVED)
            .contains(LoanStatus.READY_FOR_MANAGER, LoanStatus.AWAITING_BOARD, LoanStatus.REJECTED,
                LoanStatus.DISBURSED, LoanStatus.PAID);
    }

    @Test
    void listingMergesBothDecisionStoresReturnsLatestDecisionAndIgnoresPendingAssignments() {
        UUID loanId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        LoanApplication app = LoanApplication.builder().id(loanId).applicationNumber(42L).saccoId("SACCO-1")
            .stationId("ST-1").applicantMemberId(applicantId).loanType(LoanType.EDUCATION_LOAN)
            .amount(new BigDecimal("250000")).tenorMonths(12).status(LoanStatus.AWAITING_BOARD)
            .formData("{}").requiredGuarantors(0).policySnapshot("{}").createdAt(now.minusDays(3))
            .updatedAt(now).version(0).build();
        ManagerReview manager = ManagerReview.builder().id(UUID.randomUUID()).loanApplicationId(loanId)
            .managerMemberId(managerId).reviewStage(ApprovalWorkflowStage.MANAGER)
            .decision(ManagerDecision.ACCEPT).createdAt(now.minusDays(2)).build();
        BoardReview approved = BoardReview.builder().id(UUID.randomUUID()).loanApplicationId(loanId)
            .boardMemberId(boardId).reviewStage(ApprovalWorkflowStage.BOARD).decision(BoardDecision.APPROVED)
            .createdAt(now.minusDays(1)).decidedAt(now.minusHours(1)).build();
        BoardReview pending = BoardReview.builder().id(UUID.randomUUID()).loanApplicationId(loanId)
            .boardMemberId(UUID.randomUUID()).reviewStage(ApprovalWorkflowStage.BOARD).decision(BoardDecision.PENDING)
            .createdAt(now).build();

        when(loanApplicationRepository.findProcessedLoansPage(eq("SACCO-1"), eq("ST-1"), anyCollection(),
            eq("42"), eq(null), eq(null), eq(null), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(app)));
        when(managerReviewRepository.findByLoanApplicationIdInOrderByCreatedAtDesc(anyCollection())).thenReturn(List.of(manager));
        when(boardReviewRepository.findByLoanApplicationIdInOrderByCreatedAtDesc(anyCollection())).thenReturn(List.of(pending, approved));
        when(loanProductSettingRepository.findAllById(any())).thenReturn(List.of());
        when(memberRepository.findAllById(any())).thenReturn(List.of(
            member(applicantId, "Applicant", "MEM-1"), member(managerId, "Manager", "MGR-1"),
            member(boardId, "Board Reviewer", "BRD-1")));

        var page = service.list(principal(), " 42 ", null, null, null, 0);

        assertThat(page.rows()).hasSize(1);
        assertThat(page.rows().getFirst().decisionCount()).isEqualTo(2);
        assertThat(page.rows().getFirst().latestDecision().stage()).isEqualTo("BOARD");
        assertThat(page.rows().getFirst().latestDecision().reviewerName()).isEqualTo("Board Reviewer");
    }

    @Test
    void detailOrdersDecisionsByApprovalTimeAndIncludesCalculatedRepaymentSchedule() {
        UUID loanId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID committeeId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        LoanApplication app = LoanApplication.builder().id(loanId).applicationNumber(401L).saccoId("SACCO-1")
            .stationId("ST-1").applicantMemberId(applicantId).loanType(LoanType.EDUCATION_LOAN)
            .amount(new BigDecimal("200000")).tenorMonths(2).status(LoanStatus.CREDIT_COMMITTEE_REJECTED)
            .formData("{}").financialSnapshot("{}").attachmentsJson("[]").requiredGuarantors(0)
            .policySnapshot("{}").createdAt(now.minusDays(2)).updatedAt(now).version(0).build();
        ManagerReview manager = ManagerReview.builder().id(UUID.randomUUID()).loanApplicationId(loanId)
            .managerMemberId(managerId).reviewStage(ApprovalWorkflowStage.MANAGER)
            .decision(ManagerDecision.ACCEPT).createdAt(now.minusMinutes(10)).build();
        BoardReview committee = BoardReview.builder().id(UUID.randomUUID()).loanApplicationId(loanId)
            .boardMemberId(committeeId).reviewStage(ApprovalWorkflowStage.CREDIT_COMMITTEE)
            .decision(BoardDecision.REJECTED).comment("Reduce amount")
            .createdAt(now.minusMinutes(8)).decidedAt(now.minusMinutes(5)).build();
        List<Map<String, Object>> schedule = List.of(Map.of(
            "pmtNo", 1,
            "month", "1",
            "beginningBalance", "TSh 200,000",
            "payment", "TSh 105,000",
            "loanAmount", "TSh 100,000",
            "interest", "TSh 5,000",
            "endingBalance", "TSh 100,000"));

        when(loanApplicationRepository.findProcessedLoan(eq(loanId), eq("SACCO-1"), eq("ST-1"), anyCollection()))
            .thenReturn(Optional.of(app));
        when(managerReviewRepository.findByLoanApplicationIdInOrderByCreatedAtDesc(anyCollection())).thenReturn(List.of(manager));
        when(boardReviewRepository.findByLoanApplicationIdInOrderByCreatedAtDesc(anyCollection())).thenReturn(List.of(committee));
        when(guarantorRequestRepository.findByLoanApplicationId(loanId)).thenReturn(List.of());
        when(memberRepository.findAllById(any())).thenReturn(
            List.of(member(managerId, "Manager", "MGR-1"), member(committeeId, "Committee", "COM-1")),
            List.of(member(applicantId, "Applicant", "MEM-1")));
        when(loanPresentationService.parseFormFields("{}")).thenReturn(Map.of());
        when(loanPresentationService.parseFinancialFieldSections(app)).thenReturn(Map.of());
        when(loanPresentationService.buildProgressItems(app)).thenReturn(List.of());
        when(loanPresentationService.parseApplicationAttachments("[]")).thenReturn(List.of());
        when(loanPresentationService.reviewRepaymentSummary(app)).thenReturn(Map.of("Number of Payments", 2));
        when(loanPresentationService.isEstimatedReviewRepaymentSummary(app)).thenReturn(true);
        when(loanPresentationService.calculatedRepaymentRows(app)).thenReturn(schedule);

        var detail = service.detail(principal(), loanId);

        assertThat(detail.decisions()).extracting(ChairpersonProcessedLoanService.DecisionView::stage)
            .containsExactly("MANAGER", "CREDIT_COMMITTEE");
        assertThat(detail.repaymentSummaryEstimated()).isTrue();
        assertThat(detail.calculatedRepaymentRows()).isEqualTo(schedule);
    }

    @Test
    void listingPassesInclusiveDateRangeAndExactStatusToScopedPageQuery() {
        when(loanApplicationRepository.findProcessedLoansPage(eq("SACCO-1"), eq("ST-1"), anyCollection(),
            eq(""), any(OffsetDateTime.class), any(OffsetDateTime.class), eq(LoanStatus.DISBURSED),
            any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        var page = service.list(principal(), null, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
            LoanStatus.DISBURSED, 0);

        verify(loanApplicationRepository).findProcessedLoansPage(eq("SACCO-1"), eq("ST-1"), anyCollection(),
            eq(""), eq(OffsetDateTime.parse("2026-07-01T00:00:00+03:00")),
            eq(OffsetDateTime.parse("2026-08-01T00:00:00+03:00")), eq(LoanStatus.DISBURSED),
            any(Pageable.class));
        assertThat(page.fromDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(page.toDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(page.status()).isEqualTo(LoanStatus.DISBURSED);
        assertThat(page.availableStatuses()).contains(LoanStatus.DISBURSED).doesNotContain(LoanStatus.DRAFT);
    }

    private AppUserPrincipal principal() {
        Member member = member(UUID.randomUUID(), "Viewer", "STAFF-1");
        member.setSaccoId("SACCO-1");
        member.setStationId("ST-1");
        member.setPosition(Position.MANAGER);
        member.setStaffRoles(new LinkedHashSet<>(List.of(Position.MANAGER)));
        member.setStaffAccessStatus(StaffAccessStatus.ACTIVE);
        return new AppUserPrincipal(member, Set.of(UserClaim.PROCESSED_LOANS_VIEW), true);
    }

    private Member member(UUID id, String name, String number) {
        return Member.builder().id(id).saccoId("SACCO-1").stationId("ST-1").memberNo(number).staffNo(number)
            .fullName(name).memberAccount(false).staffAccessStatus(StaffAccessStatus.ACTIVE)
            .status(MemberStatus.ACTIVE).passwordHash("x").createdAt(OffsetDateTime.now()).build();
    }
}
