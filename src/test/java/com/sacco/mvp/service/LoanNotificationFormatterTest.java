package com.sacco.mvp.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.OutboxEvent;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoanNotificationFormatterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void formatsStaffReviewRequiredContent() throws Exception {
        UUID loanId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        LoanApplicationRepository loanRepository = mock(LoanApplicationRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        LoanNotificationFormatter formatter = formatter(loanRepository, memberRepository);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan(loanId, applicantId, LoanStatus.AWAITING_BOARD)));
        when(memberRepository.findById(applicantId)).thenReturn(Optional.of(applicant(applicantId)));

        NotificationDeliveryService.DeliveryContent content = formatter.format(
            event(loanId, "BOARD_REVIEW_ASSIGNED"),
            payload("{}"),
            NotificationDeliveryService.DeliveryContent.plain("Fallback", "Fallback body")
        );

        assertThat(content.subject()).isEqualTo("Loan application review required");
        assertThat(content.plainText()).contains("Board Member", "Loan Application ID: 42", "Applicant: Grace Member (MEM-42)");
        assertThat(content.smsText()).contains("View Loan Details: https://sacco.example/loan-notifications/" + loanId + "/open");
        assertThat(content.html()).contains("View Loan Details", "Loan Application Review Required By Board Member");
    }

    @Test
    void formatsApplicantStatusChangedContentWithReason() throws Exception {
        UUID loanId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        LoanApplicationRepository loanRepository = mock(LoanApplicationRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        LoanNotificationFormatter formatter = formatter(loanRepository, memberRepository);
        when(loanRepository.findById(loanId)).thenReturn(Optional.of(loan(loanId, applicantId, LoanStatus.MANAGER_REJECTED)));

        NotificationDeliveryService.DeliveryContent content = formatter.format(
            event(loanId, "MANAGER_REJECTED"),
            payload("{\"details\":{\"reasons\":\"Insufficient savings\"}}"),
            NotificationDeliveryService.DeliveryContent.plain("Fallback", "Fallback body")
        );

        assertThat(content.subject()).isEqualTo("Loan application status updated");
        assertThat(content.plainText()).contains("Status: Manager Rejected", "Reason: Insufficient savings");
        assertThat(content.smsText()).contains("View Loan Details: https://sacco.example/app/loan-applications/" + loanId);
        assertThat(content.html()).contains("Loan Application Status Updated", "Insufficient savings");
    }

    @Test
    void missingLoanUsesFallbackContent() throws Exception {
        UUID loanId = UUID.randomUUID();
        LoanApplicationRepository loanRepository = mock(LoanApplicationRepository.class);
        MemberRepository memberRepository = mock(MemberRepository.class);
        LoanNotificationFormatter formatter = formatter(loanRepository, memberRepository);
        NotificationDeliveryService.DeliveryContent fallback =
            NotificationDeliveryService.DeliveryContent.plain("Fallback", "Fallback body");
        when(loanRepository.findById(loanId)).thenReturn(Optional.empty());

        NotificationDeliveryService.DeliveryContent content = formatter.format(
            event(loanId, "LOAN_READY_FOR_MANAGER"),
            payload("{}"),
            fallback
        );

        assertThat(content).isSameAs(fallback);
    }

    private LoanNotificationFormatter formatter(LoanApplicationRepository loanRepository,
                                                MemberRepository memberRepository) {
        LoanNotificationFormatter formatter = new LoanNotificationFormatter(loanRepository, memberRepository);
        ReflectionTestUtils.setField(formatter, "baseUrl", "https://sacco.example");
        return formatter;
    }

    private OutboxEvent event(UUID loanId, String eventType) {
        return OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType("LOAN")
            .aggregateId(loanId)
            .eventType(eventType)
            .payload("{}")
            .createdAt(OffsetDateTime.now())
            .build();
    }

    private JsonNode payload(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private LoanApplication loan(UUID loanId, UUID applicantId, LoanStatus status) {
        return LoanApplication.builder()
            .id(loanId)
            .applicationNumber(42L)
            .applicantMemberId(applicantId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("1200000.00"))
            .status(status)
            .build();
    }

    private Member applicant(UUID applicantId) {
        return Member.builder()
            .id(applicantId)
            .memberNo("MEM-42")
            .fullName("Grace Member")
            .build();
    }
}
