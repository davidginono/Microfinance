package com.sacco.mvp.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.LoanProductDisplayService;
import com.sacco.mvp.service.LoanWorkflowService;
import com.sacco.mvp.service.StationOtpDeliveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppControllerDirectGuarantorOtpNotificationTest {
    @Mock private LoanWorkflowService loanWorkflowService;
    @Mock private EmailOtpService emailOtpService;
    @Mock private LoanProductDisplayService loanProductDisplayService;
    @Mock private MemberRepository memberRepository;
    @Mock private GuarantorRequestRepository guarantorRequestRepository;
    @Mock private AppUserPrincipal principal;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private AppController controller;

    @Test
    void directGuarantorOtpNotificationNamesApplicantAndExactLoanProduct() {
        UUID loanId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        LoanApplication application = LoanApplication.builder()
            .id(loanId)
            .applicationNumber(100401L)
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(applicantId)
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .loanProductSettingId(UUID.randomUUID())
            .amount(new BigDecimal("200000"))
            .tenorMonths(2)
            .status(LoanStatus.DRAFT)
            .requiredGuarantors(1)
            .formData("{\"guarantorApprovalMode\":\"DIRECT_OTP\"}")
            .build();
        GuarantorRequest request = GuarantorRequest.builder()
            .id(requestId)
            .loanApplicationId(loanId)
            .guarantorMemberId(guarantorId)
            .status(GuarantorRequestStatus.PENDING)
            .build();
        Member applicant = Member.builder()
            .id(applicantId)
            .fullName("Asha Applicant")
            .build();
        Member guarantor = Member.builder()
            .id(guarantorId)
            .fullName("George Guarantor")
            .email("guarantor@example.com")
            .phone("+255700000002")
            .build();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(10);

        when(principal.getMemberId()).thenReturn(applicantId);
        when(loanWorkflowService.getMine(loanId, applicantId)).thenReturn(application);
        when(guarantorRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(memberRepository.findById(guarantorId)).thenReturn(Optional.of(guarantor));
        when(memberRepository.findById(applicantId)).thenReturn(Optional.of(applicant));
        when(loanProductDisplayService.displayName(application)).thenReturn("Watumishi Emergency Loan");
        when(emailOtpService.issueOtpWithMetadata(
            anyString(),
            eq(EmailOtpPurpose.GUARANTOR_APPLICANT_CONFIRMATION),
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        )).thenReturn(new EmailOtpService.OtpIssueResult(
            true,
            new StationOtpDeliveryService.DeliveryReceipt(
                OtpDeliveryChannel.EMAIL,
                "We sent a guarantor OTP code to your email."
            ),
            expiresAt,
            600,
            expiresAt,
            0,
            3,
            3
        ));

        var response = controller.requestApplicantGuarantorConfirmationOtpJson(loanId, requestId, principal);

        ArgumentCaptor<String> introCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailOtpService).issueOtpWithMetadata(
            eq("guarantor@example.com"),
            eq(EmailOtpPurpose.GUARANTOR_APPLICANT_CONFIRMATION),
            eq(guarantorId),
            eq("Your SACCO guarantor confirmation code"),
            introCaptor.capture(),
            eq("SACCO-1"),
            eq("ST-1"),
            eq("+255700000002")
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("valid", true);
        assertThat(introCaptor.getValue())
            .contains("Share this OTP with Asha Applicant only")
            .contains("Applicant: Asha Applicant")
            .contains("Application #100401")
            .contains("Amount: TSh 200,000")
            .contains("Tenure: 2 month(s)")
            .contains("Loan Product: Watumishi Emergency Loan")
            .doesNotContain("Product: Loan Product");
    }
}
