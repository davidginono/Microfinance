package com.sacco.mvp.service;

import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanAnalyticsServiceTest {
    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private ManagerReviewRepository managerReviewRepository;
    @Mock private BoardReviewRepository boardReviewRepository;
    @Mock private LoanProductSettingRepository loanProductSettingRepository;

    @Test
    void staffProductPerformanceUsesLoansHandledByTheStaffMember() {
        UUID staffId = UUID.randomUUID();
        UUID paidLoanId = UUID.randomUUID();
        UUID defaultedLoanId = UUID.randomUUID();
        UUID rejectedLoanId = UUID.randomUUID();
        AppUserPrincipal principal = principal(staffId, Position.MANAGER);
        OffsetDateTime now = OffsetDateTime.now();
        LoanAnalyticsService service = new LoanAnalyticsService(
            loanApplicationRepository,
            managerReviewRepository,
            boardReviewRepository,
            loanProductSettingRepository,
            new ApplicationClock("Africa/Nairobi")
        );

        when(managerReviewRepository.findForAnalytics(
            any(), any(), any(), any()
        ))
            .thenReturn(List.of(
                review(paidLoanId, staffId, ManagerDecision.ACCEPT, now.minusDays(2)),
                review(defaultedLoanId, staffId, ManagerDecision.ACCEPT, now.minusDays(1)),
                review(rejectedLoanId, staffId, ManagerDecision.REJECT, now)
            ));
        when(loanApplicationRepository.findAllById(any()))
            .thenReturn(List.of(
                loan(paidLoanId, LoanType.EDUCATION_LOAN, LoanStatus.PAID),
                loan(defaultedLoanId, LoanType.EMERGENCY_LOAN, LoanStatus.DEFAULTED),
                loan(rejectedLoanId, LoanType.DEVELOPMENT_LOAN, LoanStatus.MANAGER_REJECTED)
            ));

        List<LoanAnalyticsService.LoanProductPerformance> performance =
            service.productPerformanceForStaff(principal, now.minusDays(7).toLocalDate(), now.toLocalDate(), null);
        LoanAnalyticsService.StaffPortfolioSummary portfolio =
            service.staffPortfolio(principal, now.minusDays(7).toLocalDate(), now.toLocalDate(), null, null);

        assertThat(performance)
            .filteredOn(item -> item.label().equals("Education Loan"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.totalLoans()).isEqualTo(1);
                assertThat(item.paidLoans()).isEqualTo(1);
            });
        assertThat(performance)
            .filteredOn(item -> item.label().equals("Emergency Loan"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.totalLoans()).isEqualTo(1);
                assertThat(item.defaultedLoans()).isEqualTo(1);
            });
        assertThat(portfolio.handledLoans()).isEqualTo(3);
        assertThat(portfolio.approvedLoans()).isEqualTo(2);
        assertThat(portfolio.rejectedLoans()).isEqualTo(1);
        assertThat(portfolio.defaultedAfterApproval()).isEqualTo(1);
        assertThat(portfolio.defaultedAfterApprovalRate()).isEqualByComparingTo("50.00");
    }

    @Test
    void productPerformanceSeparatesConfiguredProductsThatShareALoanType() {
        UUID staffId = UUID.randomUUID();
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        UUID firstLoanId = UUID.randomUUID();
        UUID secondLoanId = UUID.randomUUID();
        AppUserPrincipal principal = principal(staffId, Position.MANAGER);
        OffsetDateTime now = OffsetDateTime.now();
        LoanAnalyticsService service = new LoanAnalyticsService(
            loanApplicationRepository,
            managerReviewRepository,
            boardReviewRepository,
            loanProductSettingRepository,
            new ApplicationClock("Africa/Nairobi")
        );
        when(managerReviewRepository.findForAnalytics(any(), any(), any(), any()))
            .thenReturn(List.of(
                review(firstLoanId, staffId, ManagerDecision.ACCEPT, now.minusDays(1)),
                review(secondLoanId, staffId, ManagerDecision.ACCEPT, now)
            ));
        when(loanApplicationRepository.findAllById(any()))
            .thenReturn(List.of(
                loan(firstLoanId, firstProductId, LoanStatus.PAID),
                loan(secondLoanId, secondProductId, LoanStatus.DEFAULTED)
            ));
        when(loanProductSettingRepository.findBySaccoIdAndActiveTrue("SACCO-01"))
            .thenReturn(List.of(
                product(firstProductId, "Education Standard", 1),
                product(secondProductId, "Education Plus", 2)
            ));

        List<LoanAnalyticsService.LoanProductPerformance> performance =
            service.productPerformanceForStaff(principal, now.minusDays(7).toLocalDate(), now.toLocalDate(), null);

        assertThat(performance).filteredOn(item -> item.label().equals("Education Standard"))
            .singleElement().satisfies(item -> {
                assertThat(item.totalLoans()).isEqualTo(1);
                assertThat(item.paidLoans()).isEqualTo(1);
                assertThat(item.defaultedLoans()).isZero();
            });
        assertThat(performance).filteredOn(item -> item.label().equals("Education Plus"))
            .singleElement().satisfies(item -> {
                assertThat(item.totalLoans()).isEqualTo(1);
                assertThat(item.paidLoans()).isZero();
                assertThat(item.defaultedLoans()).isEqualTo(1);
            });
    }

    private AppUserPrincipal principal(UUID memberId, Position position) {
        Member member = Member.builder()
            .id(memberId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .memberNo("STAFF1")
            .fullName("Staff One")
            .memberAccount(true)
            .status(MemberStatus.ACTIVE)
            .position(position)
            .staffRoles(new LinkedHashSet<>(List.of(position)))
            .passwordHash("x")
            .createdAt(OffsetDateTime.now())
            .build();
        return new AppUserPrincipal(member, Collections.emptySet());
    }

    private ManagerReview review(UUID loanId, UUID staffId, ManagerDecision decision, OffsetDateTime createdAt) {
        return ManagerReview.builder()
            .id(UUID.randomUUID())
            .loanApplicationId(loanId)
            .managerMemberId(staffId)
            .reviewStage(ApprovalWorkflowStage.MANAGER)
            .decision(decision)
            .createdAt(createdAt)
            .build();
    }

    private LoanApplication loan(UUID loanId, LoanType loanType, LoanStatus status) {
        return LoanApplication.builder()
            .id(loanId)
            .saccoId("SACCO-01")
            .stationId("ST-1")
            .applicantMemberId(UUID.randomUUID())
            .loanType(loanType)
            .amount(BigDecimal.TEN)
            .tenorMonths(12)
            .status(status)
            .formData("{}")
            .requiredGuarantors(0)
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();
    }

    private LoanApplication loan(UUID loanId, UUID productId, LoanStatus status) {
        LoanApplication loan = loan(loanId, LoanType.EDUCATION_LOAN, status);
        loan.setLoanProductSettingId(productId);
        return loan;
    }

    private LoanProductSetting product(UUID id, String name, int displayOrder) {
        return LoanProductSetting.builder()
            .id(id)
            .saccoId("SACCO-01")
            .loanType(LoanType.EDUCATION_LOAN)
            .productName(name)
            .displayOrder(displayOrder)
            .active(true)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
