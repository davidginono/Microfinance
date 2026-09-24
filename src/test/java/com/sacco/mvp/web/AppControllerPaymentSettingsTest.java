package com.sacco.mvp.web;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.PaymentDestinationType;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.PaymentDetailsService;
import com.sacco.mvp.service.UserSettingsService;
import com.sacco.mvp.service.StationOtpDeliveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppControllerPaymentSettingsTest {
    @Mock private PaymentDetailsService paymentDetailsService;
    @Mock private EmailOtpService emailOtpService;
    @Mock private MemberDirectoryService memberDirectoryService;
    @Mock private UserSettingsService userSettingsService;
    @Mock private AppUserPrincipal principal;

    @InjectMocks private AppController controller;

    @Test
    void paymentSettingsPageLoadsOnlyPaymentSettingsData() {
        UUID memberId = UUID.randomUUID();
        when(principal.getMemberId()).thenReturn(memberId);
        when(paymentDetailsService.currentForMember(memberId)).thenReturn(
            new PaymentDetailsService.PaymentDetailsView(
                true,
                PaymentDestinationType.MOBILE_MONEY,
                "Mobile Money",
                "M-Pesa",
                "Jane Member",
                "255700000001",
                "Current member settings"
            )
        );
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.settings(principal, "payment-details", model);

        assertThat(view).isEqualTo("app/settings");
        assertThat(model.get("settingsSection")).isEqualTo("payment-details");
        assertThat(model).containsKey("paymentDetails").doesNotContainKey("memberSettingsLanguage");
        assertThat(model.get("paymentDestinationType")).isEqualTo("MOBILE_MONEY");
        verify(userSettingsService, never()).languageOrDefault(any());
    }

    @Test
    void invalidOtpCannotChangePaymentDetails() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId);
        when(principal.getMemberId()).thenReturn(memberId);
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(emailOtpService.validateOtp(
            member.getEmail(), EmailOtpPurpose.PAYMENT_DETAILS_CHANGE, memberId, "000000"
        )).thenThrow(new IllegalStateException("The OTP code is invalid."));

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String result = controller.updatePaymentDetails(
            principal,
            PaymentDestinationType.MOBILE_MONEY,
            "M-Pesa",
            "Jane Member",
            "255700000001",
            "000000",
            redirect
        );

        assertThat(result).isEqualTo("redirect:/app/settings?section=payment-details#payment-details");
        assertThat(redirect.getFlashAttributes().get("error")).isEqualTo("The OTP code is invalid.");
        verify(paymentDetailsService, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void validOtpIsConsumedAfterPaymentDetailsChange() {
        UUID memberId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        Member member = member(memberId);
        when(principal.getMemberId()).thenReturn(memberId);
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        when(emailOtpService.validateOtp(
            member.getEmail(), EmailOtpPurpose.PAYMENT_DETAILS_CHANGE, memberId, "123456"
        )).thenReturn(tokenId);

        controller.updatePaymentDetails(
            principal,
            PaymentDestinationType.MOBILE_MONEY,
            "M-Pesa",
            "Jane Member",
            "255700000001",
            "123456",
            new RedirectAttributesModelMap()
        );

        verify(paymentDetailsService).update(
            memberId, PaymentDestinationType.MOBILE_MONEY, "M-Pesa", "Jane Member", "255700000001"
        );
        verify(emailOtpService).consumeOtpById(tokenId);
    }

    @Test
    void paymentOtpRequestUsesDedicatedPurposeAndStationPolicy() {
        UUID memberId = UUID.randomUUID();
        Member member = member(memberId);
        when(principal.getMemberId()).thenReturn(memberId);
        when(memberDirectoryService.find(memberId)).thenReturn(Optional.of(member));
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(10);
        when(emailOtpService.issueOtpWithMetadata(
            eq(member.getEmail()),
            eq(EmailOtpPurpose.PAYMENT_DETAILS_CHANGE),
            eq(memberId),
            any(),
            any(),
            eq(member.getSaccoId()),
            eq(member.getStationId()),
            eq(member.getPhone())
        )).thenReturn(new EmailOtpService.OtpIssueResult(
            true,
            new StationOtpDeliveryService.DeliveryReceipt(
                com.sacco.mvp.domain.OtpDeliveryChannel.EMAIL,
                "We sent an OTP code to your registered email."
            ),
            expiresAt,
            600,
            expiresAt,
            0,
            3,
            3
        ));

        var response = controller.requestPaymentDetailsOtp(principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("valid", true);
    }

    private Member member(UUID id) {
        return Member.builder()
            .id(id)
            .email("member@example.com")
            .phone("+255700000001")
            .saccoId("SACCO-1")
            .stationId("ST-1")
            .build();
    }
}
