package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffMfaServiceTest {
    @Mock private MemberRepository memberRepository;
    @Mock private EmailOtpService emailOtpService;
    @Mock private UserClaimService userClaimService;
    @Mock private StationOtpSettingsService stationOtpSettingsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void continuesPendingChallengeWhenUserLoginMfaWasDisabled() {
        UUID memberId = UUID.randomUUID();
        StaffMfaService service = service();
        MockHttpServletRequest request = requestWithPendingChallenge(memberId);
        Member member = staffMember(memberId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(stationOtpSettingsService.requiresLoginMfa(memberId)).thenReturn(false);
        when(userClaimService.effectiveClaims(eq(memberId), anyCollection(), eq(false)))
            .thenReturn(Set.of(UserClaim.MANAGER_QUEUE_VIEW));

        StaffMfaService.ChallengeCompletion completion =
            service.continueWithoutChallengeIfNoLongerRequired(request);

        assertThat(completion).isNotNull();
        assertThat(completion.landing()).isEqualTo("/manager/loan-applications");
        assertThat(completion.principal().hasMetadataRole(Position.MANAGER)).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
            .isInstanceOf(AppUserPrincipal.class);
        HttpSession session = request.getSession(false);
        assertThat(session.getAttribute(StaffMfaService.PENDING_MEMBER_ID_ATTR)).isNull();
        verifyNoInteractions(emailOtpService);
    }

    @Test
    void keepsPendingChallengeWhenUserLoginMfaStillRequired() {
        UUID memberId = UUID.randomUUID();
        StaffMfaService service = service();
        MockHttpServletRequest request = requestWithPendingChallenge(memberId);
        Member member = staffMember(memberId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(stationOtpSettingsService.requiresLoginMfa(memberId)).thenReturn(true);

        StaffMfaService.ChallengeCompletion completion =
            service.continueWithoutChallengeIfNoLongerRequired(request);

        assertThat(completion).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false).getAttribute(StaffMfaService.PENDING_MEMBER_ID_ATTR))
            .isEqualTo(memberId);
        verifyNoInteractions(emailOtpService, userClaimService);
    }

    private StaffMfaService service() {
        return new StaffMfaService(
            memberRepository,
            emailOtpService,
            userClaimService,
            stationOtpSettingsService
        );
    }

    private MockHttpServletRequest requestWithPendingChallenge(UUID memberId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession(true);
        session.setAttribute(StaffMfaService.PENDING_MEMBER_ID_ATTR, memberId);
        session.setAttribute(StaffMfaService.PENDING_EMAIL_ATTR, "manager@example.com");
        session.setAttribute(StaffMfaService.PENDING_LOGIN_TYPE_ATTR, "staff-password");
        session.setAttribute(StaffMfaService.PENDING_LANDING_ATTR, "/manager/loan-applications");
        return request;
    }

    private Member staffMember(UUID memberId) {
        return Member.builder()
            .id(memberId)
            .saccoId("SACCO-01")
            .stationId("ST-01")
            .memberNo("M001")
            .staffNo("STAFF001")
            .fullName("Station Manager")
            .email("manager@example.com")
            .memberAccount(false)
            .staffAccessStatus(StaffAccessStatus.ACTIVE)
            .status(MemberStatus.ACTIVE)
            .position(Position.MANAGER)
            .passwordHash("secret")
            .createdAt(OffsetDateTime.now())
            .build();
    }
}
