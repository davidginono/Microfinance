package com.sacco.mvp.web;

import com.sacco.mvp.domain.EmailOtpPurpose;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.SaccoAccessStatus;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.service.AdminScopeService;
import com.sacco.mvp.service.AuditService;
import com.sacco.mvp.service.EmailOtpService;
import com.sacco.mvp.service.MemberDirectoryService;
import com.sacco.mvp.service.MemberRegistrationService;
import com.sacco.mvp.service.SaccoRegistryService;
import com.sacco.mvp.service.UserClaimService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerSaccoAccessTest {
    @Test
    void anonymousRootShowsPublicLandingPage() {
        assertThat(controller().root(null)).isEqualTo("redirect:/login");
    }

    @Mock private MemberRegistrationService memberRegistrationService;
    @Mock private EmailOtpService emailOtpService;
    @Mock private MemberDirectoryService memberDirectoryService;
    @Mock private UserClaimService userClaimService;
    @Mock private SaccoRegistryService saccoRegistryService;
    @Mock private AdminScopeService adminScopeService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"manager.one", "manager@example.com"})
    void staffPasswordRecoveryUsesEmailOrUsername(String login) {
        Member staff = staff(Position.MANAGER);
        staff.setStaffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE);
        when(memberDirectoryService.findStaffLoginAccount(login.toUpperCase(java.util.Locale.ROOT))).thenReturn(Optional.of(staff));
        when(passwordEncoder.encode("NewTestPassword")).thenReturn("encoded-password");
        assertThat(controller().requestPasswordResetOtp(login, "staff").getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(controller().verifyPasswordResetOtp(login, "staff", "123456").getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(controller().savePasswordReset(login, "staff", "123456", "NewTestPassword", "NewTestPassword").getStatusCode().is2xxSuccessful()).isTrue();
        verify(emailOtpService).validateOtp(staff.getEmail(), EmailOtpPurpose.PASSWORD_RESET, staff.getId(), "123456");
        verify(emailOtpService).consumeOtp(staff.getEmail(), EmailOtpPurpose.PASSWORD_RESET, staff.getId(), "123456");
        verify(memberDirectoryService).updatePasswordHash(staff, "encoded-password");
    }

    @Test
    void staffOtpVerifyIsBlockedBeforeConsumingOtpWhenSaccoIsSuspended() {
        Member staff = staff(Position.MANAGER);
        when(memberDirectoryService.findByEmail("manager@example.com")).thenReturn(Optional.of(staff));
        when(saccoRegistryService.findStation("SACCO-01", "AR704")).thenReturn(Optional.of(suspendedStation(null)));

        ResponseEntity<Map<String, Object>> response = controller().verifyStaffLoginOtp(
            "manager@example.com",
            "123456",
            new MockHttpServletRequest()
        );

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).containsEntry("valid", false);
        assertThat(response.getBody()).containsEntry("message", "This station workspace has been suspended. Contact the platform administrator.");
        verify(emailOtpService, never()).consumeOtp("manager@example.com", EmailOtpPurpose.STAFF_LOGIN, "123456");
    }

    @Test
    void ordinaryStaffOtpLoginRejectsPlatformAdminIdentity() {
        Member admin = staff(Position.ADMIN);
        when(memberDirectoryService.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        ResponseEntity<Map<String, Object>> response = controller().requestStaffLoginOtp("admin@example.com");

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).containsEntry("valid", false);
        verify(emailOtpService, never()).issueOtp(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString()
        );
        verify(saccoRegistryService, never()).findStation(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private AuthController controller() {
        return new AuthController(saccoRegistryService,emailOtpService,memberDirectoryService,userClaimService,adminScopeService,passwordEncoder,auditService);
    }
    @Test void staffOtpUsesAssignedOfficesWithNoProfileOrOperationalClaims() {
        for(var office:com.sacco.mvp.staffoffice.model.StaffOffice.values()) {
            var staff=staff(office.getGroup());staff.setStaffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE);
            when(memberDirectoryService.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));
            when(userClaimService.effectiveClaims(staff.getId(),staff.getActiveStaffRolesResolved(),false)).thenReturn(java.util.Set.of());
            var response=controller().verifyStaffLoginOtp(staff.getEmail(),"123456",new MockHttpServletRequest());
            assertThat(response.getBody()).containsEntry("valid",true).containsEntry("redirectUrl",office.getDashboard());
        }
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private Member member(boolean memberAccess) {
        return Member.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("AR704")
            .memberNo("MEM001")
            .email("member@example.com")
            .fullName("Member One")
            .memberAccount(memberAccess)
            .status(MemberStatus.ACTIVE)
            .passwordHash("secret")
            .build();
    }

    private Member staff(Position position) {
        return Member.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("AR704")
            .memberNo(position.name())
            .email(position == Position.ADMIN ? "admin@example.com" : "manager@example.com")
            .fullName("Staff One")
            .staffRoles(new LinkedHashSet<>(List.of(position)))
            .memberAccount(false)
            .status(MemberStatus.ACTIVE)
            .passwordHash("secret")
            .build();
    }

    private SaccoStation suspendedStation(String reason) {
        return SaccoStation.builder()
            .id(UUID.randomUUID())
            .saccoId("SACCO-01")
            .stationId("AR704")
            .active(true)
            .accessStatus(SaccoAccessStatus.SUSPENDED)
            .accessRestrictionReason(reason)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
