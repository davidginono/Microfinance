package com.sacco.mvp.clients.onboarding;

import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.clients.onboarding.repository.OnboardingRepository;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientContactCheckTest {
    final MemberRepository members=mock(MemberRepository.class);
    final GeneralLedgerService access=mock(GeneralLedgerService.class);
    final MemberRegistrationService registration=new MemberRegistrationService(members,null,null,null,null,null,null,null);
    final OnboardingService service=new OnboardingService(mock(OnboardingRepository.class),access,registration,null,null,null,null,null,null,null);
    final AppUserPrincipal actor=actor(EnumSet.allOf(UserClaim.class));
    AppUserPrincipal actor(Set<UserClaim> claims) {return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),claims,true);}

    @Test void normalizationUsesExistingContactKeysAndReturnsOnlyPresence() {
        when(members.existsByPhone("255712345678")).thenReturn(true);
        assertThat(service.checkContact(actor,null,"phone","0712345678").exists()).isTrue();
        assertThat(service.checkContact(actor,null,"phone","+255712345678").exists()).isTrue();
        when(members.existsByEmailIgnoreCase("name@example.com")).thenReturn(true);
        assertThat(service.checkContact(actor,null,"email"," NAME@EXAMPLE.COM ").exists()).isTrue();
        verify(access,times(3)).requireActor(actor,"CLIENT_ONBOARDING_VIEW");
        verify(access,times(3)).requireActor(actor,"CLIENT_ONBOARDING_CREATE");
    }
    @Test void malformedAndEmptyOptionalValuesDoNotQueryContacts() {
        for(String phone:List.of("","123","25571234567800000"))assertThat(service.checkContact(actor,null,"phone",phone).valid()).isFalse();
        for(String email:List.of("name@","bad value@example.com","x".repeat(256)))assertThat(service.checkContact(actor,null,"email",email).valid()).isFalse();
        assertThat(service.checkContact(actor,null,"email","").exists()).isFalse();
        assertThatThrownBy(()->service.checkContact(actor,null,"identityNumber","123")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(members);
    }
    @Test void editExcludesOnlyTheAuthorizedProvisionalClient() {
        UUID id=UUID.randomUUID();when(members.findByIdAndSaccoIdAndStationIdIgnoreCase(id,"I1","B1")).thenReturn(Optional.of(Member.builder().id(id).position(Position.MEMBER).memberAccount(true).clientPortalEnabled(false).build()));
        assertThat(service.checkContact(actor,id,"phone","0712345678").exists()).isFalse();
        verify(members).existsByPhoneAndIdNot("255712345678",id);verify(access).requireActor(actor,"CLIENT_ONBOARDING_UPDATE");
        verify(members,never()).existsByPhone(anyString());
    }
    @Test void foreignOrPortalEnabledExclusionsAndMissingClaimsCannotProbeContacts() {
        UUID id=UUID.randomUUID();when(members.findByIdAndSaccoIdAndStationIdIgnoreCase(id,"I1","B1")).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.checkContact(actor,id,"phone","0712345678")).isInstanceOf(AccessDeniedException.class);
        when(members.findByIdAndSaccoIdAndStationIdIgnoreCase(id,"I1","B1")).thenReturn(Optional.of(Member.builder().id(id).clientPortalEnabled(true).build()));
        assertThatThrownBy(()->service.checkContact(actor,id,"phone","0712345678")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.checkContact(actor(Set.of(UserClaim.CLIENT_ONBOARDING_VIEW)),null,"phone","0712345678")).isInstanceOf(AccessDeniedException.class);
        verify(members,never()).existsByPhone(anyString());verify(members,never()).existsByPhoneAndIdNot(anyString(),any());
    }
}
