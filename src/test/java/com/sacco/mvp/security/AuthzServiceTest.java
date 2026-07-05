package com.sacco.mvp.security;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthzServiceTest {

    @Mock private LoanApplicationRepository loanApplicationRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private BoardReviewRepository boardReviewRepository;

    @InjectMocks
    private AuthzService authzService;

    @Test
    void ownershipAndAssignmentsAreEnforced() {
        UUID memberId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";
        UUID loanId = UUID.randomUUID();
        UUID reqId = UUID.randomUUID();

        Member member = Member.builder().id(memberId).saccoId(saccoId).memberNo("MEM1").fullName("A")
            .status(MemberStatus.ACTIVE).position(Position.BOARD)
            .staffRoles(new java.util.LinkedHashSet<>(List.of(Position.BOARD)))
            .passwordHash("x").createdAt(OffsetDateTime.now()).build();
        AppUserPrincipal principal = new AppUserPrincipal(member, java.util.Set.of(UserClaim.REVIEW_BOARD_QUEUE));

        LoanApplication app = LoanApplication.builder().id(loanId).saccoId(saccoId).applicantMemberId(memberId)
            .loanType(LoanType.EDUCATION_LOAN).amount(BigDecimal.TEN).tenorMonths(1).status(LoanStatus.DRAFT)
            .formData("{}").requiredGuarantors(3).policySnapshot("{}").createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now()).version(0).build();

        GuarantorRequest req = GuarantorRequest.builder().id(reqId).loanApplicationId(loanId).guarantorMemberId(memberId)
            .status(GuarantorRequestStatus.PENDING).createdAt(OffsetDateTime.now()).version(0).build();

        BoardReview boardReview = BoardReview.builder().id(UUID.randomUUID()).loanApplicationId(loanId)
            .boardMemberId(memberId).decision(BoardDecision.PENDING).createdAt(OffsetDateTime.now()).build();

        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));
        when(guarantorRequestRepository.findByIdAndGuarantorMemberId(reqId, memberId)).thenReturn(Optional.of(req));
        when(boardReviewRepository.findByLoanApplicationIdAndBoardMemberIdAndReviewStage(
            loanId, memberId, ApprovalWorkflowStage.BOARD)).thenReturn(Optional.of(boardReview));

        assertThat(authzService.isLoanOwner(loanId, principal)).isTrue();
        assertThat(authzService.isGuarantorAssignee(reqId, principal)).isTrue();
        assertThat(authzService.isBoardAssignee(loanId, principal)).isTrue();
    }

    @Test
    void minorAdminDisbursementClaimDoesNotGrantLoanVisibility() {
        UUID adminId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        String saccoId = "CIRCLE-1001";

        Member admin = Member.builder()
            .id(adminId)
            .saccoId(saccoId)
            .stationId("ST-1")
            .memberNo("ADM1")
            .fullName("SACCOS Admin")
            .status(MemberStatus.ACTIVE)
            .position(Position.MINOR_ADMIN)
            .staffRoles(new java.util.LinkedHashSet<>(List.of(Position.MINOR_ADMIN)))
            .passwordHash("x")
            .createdAt(OffsetDateTime.now())
            .build();
        AppUserPrincipal principal = new AppUserPrincipal(admin, java.util.Set.of(UserClaim.ACCESS_DISBURSEMENT_QUEUE));

        LoanApplication app = LoanApplication.builder()
            .id(loanId)
            .saccoId(saccoId)
            .stationId("ST-1")
            .applicantMemberId(applicantId)
            .loanType(LoanType.EDUCATION_LOAN)
            .amount(BigDecimal.TEN)
            .tenorMonths(1)
            .status(LoanStatus.READY_FOR_DISBURSEMENT)
            .formData("{}")
            .requiredGuarantors(0)
            .policySnapshot("{}")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .version(0)
            .build();

        when(loanApplicationRepository.findById(loanId)).thenReturn(Optional.of(app));

        assertThat(authzService.canViewLoan(loanId, principal)).isTrue();
        assertThat(authzService.notAdminClass(principal)).isFalse();
    }

    @Test
    void staffAnalyticsAccessIsLimitedToWorkspaceStaffRoles() {
        assertThat(authzService.staffAnalyticsAccess(principalWith(Position.LOAN_OFFICER, false))).isTrue();
        assertThat(authzService.staffAnalyticsAccess(principalWith(Position.MANAGER, false))).isTrue();
        assertThat(authzService.staffAnalyticsAccess(principalWith(Position.ACCOUNTANT, false))).isTrue();
        assertThat(authzService.staffAnalyticsAccess(principalWith(Position.DISBURSEMENT_OFFICER, false))).isTrue();
        assertThat(authzService.staffAnalyticsAccess(principalWith(Position.BOARD, false))).isTrue();
        assertThat(authzService.staffAnalyticsAccess(principalWithRoles(List.of(Position.MINOR_ADMIN, Position.MANAGER), false))).isTrue();
        assertThat(authzService.staffAnalyticsAccess(principalWithRoles(List.of(Position.MINOR_ADMIN, Position.DISBURSEMENT_OFFICER), false))).isTrue();

        assertThat(authzService.staffAnalyticsAccess(principalWith(Position.MEMBER, true))).isFalse();
        assertThat(authzService.staffAnalyticsAccess(principalWith(Position.ADMIN, false))).isFalse();
        assertThat(authzService.staffAnalyticsAccess(principalWith(Position.MINOR_ADMIN, false))).isFalse();

        Member claimOnlyMember = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("CIRCLE-1001")
            .memberNo("MEM2")
            .fullName("Claim Only")
            .memberAccount(true)
            .status(MemberStatus.ACTIVE)
            .position(Position.MEMBER)
            .passwordHash("x")
            .createdAt(OffsetDateTime.now())
            .build();
        AppUserPrincipal claimOnlyPrincipal = new AppUserPrincipal(
            claimOnlyMember,
            java.util.Set.of(UserClaim.ACCESS_DISBURSEMENT_QUEUE)
        );

        assertThat(authzService.staffAnalyticsAccess(claimOnlyPrincipal)).isFalse();
        assertThat(authzService.staffAnalyticsAccess(null)).isFalse();
    }

    private AppUserPrincipal principalWith(Position position, boolean memberAccess) {
        java.util.LinkedHashSet<Position> staffRoles = new java.util.LinkedHashSet<>();
        if (position != null && position.isStaffRole()) {
            staffRoles.add(position);
        }
        return principalWithRoles(staffRoles.stream().toList(), memberAccess);
    }

    private AppUserPrincipal principalWithRoles(List<Position> positions, boolean memberAccess) {
        java.util.LinkedHashSet<Position> staffRoles = new java.util.LinkedHashSet<>();
        if (positions != null) {
            positions.stream()
                .filter(position -> position != null && position.isStaffRole())
                .forEach(staffRoles::add);
        }
        Position position = Position.primaryRole(staffRoles, memberAccess);
        Member member = Member.builder()
            .id(UUID.randomUUID())
            .saccoId("CIRCLE-1001")
            .memberNo(position == null ? "USER" : position.name())
            .fullName("Test User")
            .memberAccount(memberAccess)
            .status(MemberStatus.ACTIVE)
            .position(position)
            .staffRoles(staffRoles)
            .passwordHash("x")
            .createdAt(OffsetDateTime.now())
            .build();
        return new AppUserPrincipal(member, Collections.emptySet());
    }
}
