package com.sacco.mvp.web;

import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.integration.foresight.UpstreamAvailabilityException;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.FormSchemaService;
import com.sacco.mvp.service.LoanProductDisplayService;
import com.sacco.mvp.service.LoanWorkflowService;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.NotificationDeliveryService;
import com.sacco.mvp.service.SmsUnitTransactionService;
import com.sacco.mvp.service.StationOtpDeliveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppControllerDirectGuarantorOtpNotificationTest {
    @Mock private LoanWorkflowService loanWorkflowService;
    @Mock private FormSchemaService formSchemaService;
    @Mock private EmailOtpService emailOtpService;
    @Mock private LoanProductDisplayService loanProductDisplayService;
    @Mock private MemberDirectoryService memberDirectoryService;
    @Mock private NotificationDeliveryService notificationDeliveryService;
    @Mock private AppUserPrincipal principal;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private AppController controller;

    @Test
    void directOtpSearchReportsMemberDirectoryOutage() {
        UUID applicantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LoanProductSetting product = LoanProductSetting.builder()
            .id(productId)
            .saccoId("SACCO-1")
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .active(true)
            .build();

        when(principal.getSaccoId()).thenReturn("SACCO-1");
        when(principal.getStationId()).thenReturn("ST-1");
        when(principal.getMemberId()).thenReturn(applicantId);
        when(formSchemaService.getSchema("SACCO-1", productId, LoanType.DEVELOPMENT_LOAN)).thenReturn(product);
        when(loanWorkflowService.searchDirectOtpGuarantorCandidates(
            "SACCO-1",
            "ST-1",
            applicantId,
            "tahasaccos@gmail.com",
            "email",
            product
        )).thenThrow(new UpstreamAvailabilityException(
            "Member directory is unavailable right now. Please try again later.",
            null
        ));

        var response = controller.searchDirectOtpGuarantors(
            principal,
            "tahasaccos@gmail.com",
            "email",
            productId,
            LoanType.DEVELOPMENT_LOAN
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody())
            .containsEntry("items", java.util.List.of())
            .containsEntry("message", "Member directory is unavailable right now. Please try again later.");
    }

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
        when(loanWorkflowService.findGuarantorRequest(requestId)).thenReturn(Optional.of(request));
        when(memberDirectoryService.find(guarantorId)).thenReturn(Optional.of(guarantor));
        when(memberDirectoryService.find(applicantId)).thenReturn(Optional.of(applicant));
        when(loanProductDisplayService.displayName(application)).thenReturn("Watumishi Emergency Loan");
        when(emailOtpService.issueLoanOtpWithMetadata(
            anyString(),
            eq(EmailOtpPurpose.GUARANTOR_APPLICANT_CONFIRMATION),
            any(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            eq(loanId),
            eq(applicantId)
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
        verify(emailOtpService).issueLoanOtpWithMetadata(
            eq("guarantor@example.com"),
            eq(EmailOtpPurpose.GUARANTOR_APPLICANT_CONFIRMATION),
            eq(guarantorId),
            eq("Your SACCO guarantor confirmation code"),
            introCaptor.capture(),
            eq("SACCO-1"),
            eq("ST-1"),
            eq("+255700000002"),
            eq(loanId),
            eq(applicantId)
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
            .contains("Guarantorship terms:")
            .contains("accept responsibility for recoveries and penalties if the applicant defaults")
            .contains("agree to the guarantor terms and conditions in the SACCO bylaws and loan contract")
            .doesNotContain("Product: Loan Product");
    }

    @Test
    void directGuarantorOtpNotificationSupportsForesightOnlyRequest() {
        UUID loanId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        LoanApplication application = LoanApplication.builder()
            .id(loanId)
            .applicationNumber(100402L)
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(applicantId)
            .loanType(LoanType.DEVELOPMENT_LOAN)
            .amount(new BigDecimal("150000"))
            .tenorMonths(3)
            .status(LoanStatus.AWAITING_GUARANTORS)
            .requiredGuarantors(1)
            .formData("{\"guarantorApprovalMode\":\"DIRECT_OTP\"}")
            .build();
        GuarantorRequest request = GuarantorRequest.builder()
            .id(requestId)
            .loanApplicationId(loanId)
            .guarantorMemberId(null)
            .guarantorSource("FORESIGHT")
            .externalMemberNo("EXT-77")
            .externalStationId("ST-1")
            .externalFullName("Asha Mtei")
            .externalEmail("asha@example.com")
            .externalPhone("+255700000003")
            .status(GuarantorRequestStatus.PENDING)
            .build();
        Member applicant = Member.builder()
            .id(applicantId)
            .fullName("Asha Applicant")
            .build();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(10);

        when(principal.getMemberId()).thenReturn(applicantId);
        when(loanWorkflowService.getMine(loanId, applicantId)).thenReturn(application);
        when(loanWorkflowService.findGuarantorRequest(requestId)).thenReturn(Optional.of(request));
        when(memberDirectoryService.find(applicantId)).thenReturn(Optional.of(applicant));
        when(loanProductDisplayService.displayName(application)).thenReturn("Development Loan");
        when(emailOtpService.issueLoanOtpWithDeliveryContact(
            anyString(),
            anyString(),
            eq(EmailOtpPurpose.GUARANTOR_APPLICANT_CONFIRMATION),
            isNull(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            eq(loanId),
            eq(applicantId)
        )).thenReturn(new EmailOtpService.OtpIssueResult(
            true,
            new StationOtpDeliveryService.DeliveryReceipt(
                OtpDeliveryChannel.SMS,
                "We sent a guarantor OTP code to the profile phone."
            ),
            expiresAt,
            600,
            expiresAt,
            0,
            3,
            3
        ));

        var response = controller.requestApplicantGuarantorConfirmationOtpJson(loanId, requestId, principal);

        verify(emailOtpService).issueLoanOtpWithDeliveryContact(
            eq("guarantor-request:" + requestId),
            eq("asha@example.com"),
            eq(EmailOtpPurpose.GUARANTOR_APPLICANT_CONFIRMATION),
            isNull(),
            eq("Your SACCO guarantor confirmation code"),
            anyString(),
            eq("SACCO-1"),
            eq("ST-1"),
            eq("+255700000003"),
            eq(loanId),
            eq(applicantId)
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("valid", true);
    }

    @Test
    void directOtpConfirmationNotifiesGuarantorThatLoanObligationIsRecorded() {
        UUID loanId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID guarantorId = UUID.randomUUID();
        UUID otpTokenId = UUID.randomUUID();
        LoanApplication application = LoanApplication.builder()
            .id(loanId)
            .applicationNumber(100403L)
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .applicantMemberId(applicantId)
            .loanType(LoanType.CUSTOMIZED_LOAN)
            .loanProductSettingId(UUID.randomUUID())
            .amount(new BigDecimal("350000"))
            .tenorMonths(4)
            .status(LoanStatus.AWAITING_GUARANTORS)
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
            .memberNo("M-22")
            .email("guarantor@example.com")
            .phone("+255700000002")
            .build();

        when(principal.getMemberId()).thenReturn(applicantId);
        when(loanWorkflowService.getMine(loanId, applicantId)).thenReturn(application);
        when(loanWorkflowService.findGuarantorRequest(requestId)).thenReturn(Optional.of(request));
        when(memberDirectoryService.find(guarantorId)).thenReturn(Optional.of(guarantor));
        when(memberDirectoryService.find(applicantId)).thenReturn(Optional.of(applicant));
        when(loanProductDisplayService.displayName(application)).thenReturn("Watumishi Emergency Loan");
        when(emailOtpService.validateOtp(
            "guarantor@example.com",
            EmailOtpPurpose.GUARANTOR_APPLICANT_CONFIRMATION,
            guarantorId,
            "123456"
        )).thenReturn(otpTokenId);

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String view = controller.confirmApplicantGuarantorOtp(
            loanId,
            requestId,
            principal,
            "123456",
            redirectAttributes
        );

        assertThat(view).isEqualTo("redirect:/app/loan-applications/" + loanId);
        assertThat(redirectAttributes.getFlashAttributes().get("message"))
            .isEqualTo("Guarantor approval confirmed.");
        verify(loanWorkflowService).approveDirectOtpGuarantorRequest(
            eq(requestId),
            eq(applicantId),
            eq("Approved by guarantor OTP"),
            any(OffsetDateTime.class)
        );
        verify(emailOtpService).consumeOtpById(otpTokenId);

        ArgumentCaptor<NotificationDeliveryService.DeliveryContent> contentCaptor =
            ArgumentCaptor.forClass(NotificationDeliveryService.DeliveryContent.class);
        ArgumentCaptor<SmsUnitTransactionService.SmsUsageContext> contextCaptor =
            ArgumentCaptor.forClass(SmsUnitTransactionService.SmsUsageContext.class);
        verify(notificationDeliveryService).deliverDirectContact(
            eq("SACCO-1"),
            eq("ST-1"),
            any(UUID.class),
            eq("guarantor@example.com"),
            eq("+255700000002"),
            eq("DIRECT_GUARANTOR_OTP_CONFIRMED"),
            contentCaptor.capture(),
            contextCaptor.capture()
        );

        NotificationDeliveryService.DeliveryContent content = contentCaptor.getValue();
        assertThat(content.subject()).isEqualTo("Guarantor confirmation recorded");
        assertThat(content.plainText())
            .contains("Your guarantor OTP has been entered by Asha Applicant.")
            .contains("successfully chosen as guarantor")
            .contains("obligated to this loan")
            .contains("Application #100403")
            .contains("Amount: TSh 350,000")
            .contains("Loan Product: Watumishi Emergency Loan");
        assertThat(content.smsText())
            .contains("Asha Applicant")
            .contains("successfully chosen as guarantor")
            .contains("obligated to this loan");
        assertThat(contextCaptor.getValue())
            .isEqualTo(new SmsUnitTransactionService.SmsUsageContext(loanId, applicantId, guarantorId));
    }
}
