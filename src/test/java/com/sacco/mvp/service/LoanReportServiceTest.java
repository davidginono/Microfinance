package com.sacco.mvp.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanReportServiceTest {

    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private ManagerReviewRepository managerReviewRepository;

    private LoanReportService loanReportService;

    @BeforeEach
    void setUp() {
        loanReportService = new LoanReportService(
            loanApplicationRepository,
            memberRepository,
            guarantorRequestRepository,
            boardReviewRepository,
            managerReviewRepository,
            JsonMapper.builder().findAndAddModules().build()
        );
    }

    @Test
    void memberLoanReportCommitteeDecisionContainsOnlyBoardMemberDecisions() {
        UUID applicantId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        UUID boardMemberId = UUID.randomUUID();
        Member applicant = Member.builder()
            .id(applicantId)
            .fullName("Applicant Name")
            .memberNo("MEM-100")
            .build();
        Member boardMember = Member.builder()
            .id(boardMemberId)
            .fullName("Board Member Name")
            .memberNo("BOARD-10")
            .build();
        LoanApplication loan = LoanApplication.builder()
            .id(loanId)
            .applicantMemberId(applicantId)
            .applicationNumber(10040L)
            .loanId("LN-10040")
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .amount(new BigDecimal("500000.00"))
            .status(LoanStatus.AWAITING_BOARD)
            .financialSnapshot("{}")
            .build();
        BoardReview boardReview = BoardReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .boardMemberId(boardMemberId)
            .decision(BoardDecision.APPROVED)
            .build();

        when(memberRepository.findById(applicantId)).thenReturn(Optional.of(applicant));
        when(loanApplicationRepository.findByApplicantMemberIdOrderByCreatedAtDesc(applicantId)).thenReturn(List.of(loan));
        when(guarantorRequestRepository.findByLoanApplicationId(loanId)).thenReturn(List.of());
        when(boardReviewRepository.findByLoanApplicationId(loanId)).thenReturn(List.of(boardReview));
        when(memberRepository.findAllById(any())).thenReturn(List.of(boardMember));

        LoanReportService.MemberLoanReport report = loanReportService.memberReport(applicantId);

        assertThat(report.details().getFirst().approvalDecisionSummaryLabel())
            .isEqualTo("Board Member Name: Approved")
            .doesNotContain("Applicant Name");
        verifyNoInteractions(managerReviewRepository);
    }
}
