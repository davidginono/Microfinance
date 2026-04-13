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
            .status(MemberStatus.ACTIVE).position(Position.MEMBER).passwordHash("x").createdAt(OffsetDateTime.now()).build();
        AppUserPrincipal principal = new AppUserPrincipal(member, Collections.emptySet());

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
        when(boardReviewRepository.findByLoanApplicationIdAndBoardMemberId(loanId, memberId)).thenReturn(Optional.of(boardReview));

        assertThat(authzService.isLoanOwner(loanId, principal)).isTrue();
        assertThat(authzService.isGuarantorAssignee(reqId, principal)).isTrue();
        assertThat(authzService.isBoardAssignee(loanId, principal)).isTrue();
    }
}
