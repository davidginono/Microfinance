package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.MinorAdminInvitation;
import com.sacco.mvp.domain.OtpDeliveryChannel;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.MinorAdminInvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinorAdminInvitationServiceTest {
    @Mock private MinorAdminInvitationRepository invitationRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private EmailOtpService emailOtpService;
    @Mock private NotificationEmailService notificationEmailService;
    @Mock private PasswordEncoder passwordEncoder;

    private MinorAdminInvitationService invitationService;

    @BeforeEach
    void setUp() {
        invitationService = new MinorAdminInvitationService(
            invitationRepository,
            memberRepository,
            emailOtpService,
            notificationEmailService,
            passwordEncoder
        );
    }

    @Test
    void requestOtpIssuesOneConfiguredChannelClaimCode() {
        String rawToken = "activation-token";
        String tokenHash = hashToken(rawToken);
        UUID memberId = UUID.randomUUID();
        MinorAdminInvitation invitation = MinorAdminInvitation.builder()
            .id(UUID.randomUUID())
            .memberId(memberId)
            .tokenHash(tokenHash)
            .expiresAt(OffsetDateTime.now().plusHours(1))
            .build();
        Member member = Member.builder()
            .id(memberId)
            .saccoId("SACCO-01")
            .stationId("AR704")
            .memberNo("CR001")
            .fullName("Juma Mabula")
            .email("ginonodavid625@gmail.com")
            .phone("255746359369")
            .status(MemberStatus.INVITED)
            .position(Position.CREDIT_COMMITTEE)
            .build();
        StationOtpDeliveryService.DeliveryReceipt receipt =
            new StationOtpDeliveryService.DeliveryReceipt(OtpDeliveryChannel.SMS, "Code sent by SMS.");

        when(invitationRepository.findByTokenHashAndClaimedAtIsNullAndRevokedAtIsNull(tokenHash))
            .thenReturn(Optional.of(invitation));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(emailOtpService.issueOtp(
            "ginonodavid625@gmail.com",
            com.sacco.mvp.domain.EmailOtpPurpose.CLAIM_ACCOUNT,
            memberId,
            "Activate your SACCO admin account",
            "Use the code below to complete the activation of your Minor Admin account for SACCO-01.",
            "SACCO-01",
            "AR704",
            "255746359369"
        )).thenReturn(receipt);

        StationOtpDeliveryService.DeliveryReceipt result = invitationService.requestOtp(rawToken);

        org.assertj.core.api.Assertions.assertThat(result).isSameAs(receipt);
    }

    @Test
    void claimInvitationConsumesOnlyAccountOtpAndActivatesStaff() {
        String rawToken = "activation-token";
        String tokenHash = hashToken(rawToken);
        UUID memberId = UUID.randomUUID();
        MinorAdminInvitation invitation = MinorAdminInvitation.builder()
            .id(UUID.randomUUID())
            .memberId(memberId)
            .tokenHash(tokenHash)
            .expiresAt(OffsetDateTime.now().plusHours(1))
            .build();
        Member member = Member.builder()
            .id(memberId)
            .saccoId("SACCO-01")
            .stationId("AR704")
            .memberNo("CR001")
            .fullName("Juma Mabula")
            .email("ginonodavid625@gmail.com")
            .phone("255746359369")
            .status(MemberStatus.INVITED)
            .position(Position.CREDIT_COMMITTEE)
            .build();

        when(invitationRepository.findByTokenHashAndClaimedAtIsNullAndRevokedAtIsNull(tokenHash))
            .thenReturn(Optional.of(invitation));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(passwordEncoder.encode("strong-password")).thenReturn("encoded-password");

        Member activated = invitationService.claimInvitation(rawToken, "123456", "strong-password", "Juma Mabula");

        org.assertj.core.api.Assertions.assertThat(activated.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        org.assertj.core.api.Assertions.assertThat(activated.getPasswordHash()).isEqualTo("encoded-password");
        org.assertj.core.api.Assertions.assertThat(activated.getSignatureText()).isEqualTo("Juma Mabula");
        org.assertj.core.api.Assertions.assertThat(activated.getSignatureRegisteredAt()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(invitation.getClaimedAt()).isNotNull();
        verify(emailOtpService).consumeOtp(
            "ginonodavid625@gmail.com",
            com.sacco.mvp.domain.EmailOtpPurpose.CLAIM_ACCOUNT,
            "123456"
        );
        verify(memberRepository).save(member);
        verify(invitationRepository).save(invitation);
    }

    @Test
    void cleanupExpiredInvitationsDeletesUnclaimedInvitedStaffMember() {
        UUID memberId = UUID.randomUUID();
        MinorAdminInvitation invitation = MinorAdminInvitation.builder()
            .id(UUID.randomUUID())
            .memberId(memberId)
            .expiresAt(OffsetDateTime.now().minusMinutes(5))
            .build();
        Member member = Member.builder()
            .id(memberId)
            .memberNo("CR001")
            .fullName("Juma Mabula")
            .status(MemberStatus.INVITED)
            .position(Position.CREDIT_COMMITTEE)
            .build();
        when(invitationRepository.findByClaimedAtIsNullAndRevokedAtIsNullAndExpiresAtBefore(
            org.mockito.ArgumentMatchers.any(OffsetDateTime.class)
        )).thenReturn(List.of(invitation));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        invitationService.cleanupExpiredInvitations();

        verify(invitationRepository).delete(invitation);
        verify(memberRepository).delete(member);
    }

    @Test
    void cleanupExpiredInvitationsLeavesActivatedStaffMember() {
        UUID memberId = UUID.randomUUID();
        MinorAdminInvitation invitation = MinorAdminInvitation.builder()
            .id(UUID.randomUUID())
            .memberId(memberId)
            .expiresAt(OffsetDateTime.now().minusMinutes(5))
            .build();
        Member member = Member.builder()
            .id(memberId)
            .memberNo("CR001")
            .status(MemberStatus.ACTIVE)
            .position(Position.CREDIT_COMMITTEE)
            .build();
        when(invitationRepository.findByClaimedAtIsNullAndRevokedAtIsNullAndExpiresAtBefore(
            org.mockito.ArgumentMatchers.any(OffsetDateTime.class)
        )).thenReturn(List.of(invitation));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        invitationService.cleanupExpiredInvitations();

        verify(invitationRepository, never()).delete(invitation);
        verify(memberRepository, never()).delete(member);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
