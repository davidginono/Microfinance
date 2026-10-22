package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffSignatureServiceTest {
    @Mock MemberRepository repository;
    @Mock PasswordEncoder encoder;
    @Mock AuditService audit;
    StaffSignatureService service;
    Member member;
    AppUserPrincipal actor;

    @BeforeEach void setup() {
        service = new StaffSignatureService(repository, new NameSignatureService(), encoder, audit);
        member = Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1")
            .position(Position.MANAGER).staffRoles(Set.of(Position.MANAGER)).staffNo("TEST")
            .status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE)
            .fullName("Test Manager").passwordHash("encoded").build();
        actor = new AppUserPrincipal(member, EnumSet.allOf(UserClaim.class), true);
        when(repository.findForSignatureRegistration(member.getId())).thenReturn(Optional.of(member));
    }

    @Test void updatesOwnSignatureWithAuditWithoutRenamingTheAccount() {
        when(encoder.matches("password", "encoded")).thenReturn(true);
        service.register(actor, "  Amina  Juma ", "password", true);
        assertThat(member.getFullName()).isEqualTo("AMINA JUMA");
        assertThat(member.getSignatureText()).isEqualTo("Amina J");
        var registeredAt = member.getSignatureRegisteredAt();
        assertThat(registeredAt).isNotNull();
        service.register(actor, "Different Name", "password", true);
        assertThat(member.getSignatureText()).isEqualTo("Different N");
        assertThat(member.getFullName()).isEqualTo("AMINA JUMA");
        verify(repository, times(2)).save(member);
        verify(audit).log(eq("STAFF_USER"), eq(member.getId()), eq("STAFF_UPDATE_OWN_SIGNATURE"), eq(member.getId()), any(), any());
        verify(audit, times(1)).log(eq("STAFF_USER"), eq(member.getId()), eq("STAFF_REGISTER_OWN_SIGNATURE"), eq(member.getId()), any(), any());
    }

    @Test void requiresExplicitConsentAndCurrentPassword() {
        assertThatThrownBy(() -> service.register(actor, "Amina Juma", "password", false)).hasMessage("profile.signature.confirmRequired");
        assertThatThrownBy(() -> service.register(actor, "Amina Juma", "wrong", true)).hasMessage("profile.signature.passwordError");
        verify(repository, never()).save(any()); verifyNoInteractions(audit);
    }

    @Test void rejectsSingleNamesAndMarkupWithoutStoringIdentity() {
        for (String name : List.of("Amina", "<script> Name", "- -", "A ".repeat(61))) {
            assertThatThrownBy(() -> service.register(actor, name, "password", true)).hasMessage("profile.signature.invalidName");
        }
        verify(repository, never()).save(any()); verifyNoInteractions(encoder, audit);
    }

    @Test void rejectsChangedBranchAndDeactivatedStaff() {
        member.setStationId("B2");
        assertThatThrownBy(() -> service.register(actor, "Amina Juma", "password", true)).isInstanceOf(AccessDeniedException.class);
        member.setStationId("B1"); member.setStaffAccessStatus(StaffAccessStatus.PENDING_ACKNOWLEDGEMENT);
        assertThatThrownBy(() -> service.register(actor, "Amina Juma", "password", true)).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any()); verifyNoInteractions(encoder, audit);
    }

    @Test void rejectsClientSessionsEvenIfTheStoredAccountLaterGainsStaffAccess() {
        var client = Member.builder().id(member.getId()).saccoId("I1").stationId("B1").position(Position.MEMBER).status(MemberStatus.ACTIVE).build();
        var clientActor = new AppUserPrincipal(client, EnumSet.noneOf(UserClaim.class), true);
        assertThatThrownBy(() -> service.register(clientActor, "Amina Juma", "password", true)).isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any()); verifyNoInteractions(encoder, audit);
    }
}
