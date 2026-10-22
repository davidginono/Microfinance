package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProfileContactServiceTest {
    final MemberRepository members=mock(MemberRepository.class);
    final PasswordEncoder passwords=mock(PasswordEncoder.class);
    final PhoneOtpService otp=mock(PhoneOtpService.class);
    final AuditService audit=mock(AuditService.class);
    final ApplicationClock clock=mock(ApplicationClock.class);
    final ProfileContactService service=new ProfileContactService(members,passwords,otp,audit,clock);
    Member member;AppUserPrincipal actor;
    final OffsetDateTime now=OffsetDateTime.parse("2026-10-22T11:00:00+03:00");
    @BeforeEach void setup(){
        member=Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("C1").fullName("Test User").position(Position.MEMBER).status(MemberStatus.ACTIVE).email("old@example.test").phone("255712345678").passwordHash("hash").build();
        actor=new AppUserPrincipal(member,Set.of(),false);
        when(members.findForSignatureRegistration(member.getId())).thenReturn(Optional.of(member));when(members.findById(member.getId())).thenReturn(Optional.of(member));
        when(passwords.matches("correct","hash")).thenReturn(true);when(clock.now()).thenReturn(now);
    }
    @Test void normalizedOwnContactsAreAuditedAndChangingThePhoneClearsItsProof(){
        member.setPhoneVerifiedAt(now.minusDays(1));service.update(actor," NEW@EXAMPLE.TEST ","0712345679","correct");
        assertThat(member.getEmail()).isEqualTo("new@example.test");assertThat(member.getPhone()).isEqualTo("255712345679");assertThat(member.getPhoneVerifiedAt()).isNull();
        verify(audit).log(eq("USER"),eq(member.getId()),eq("USER_UPDATE_OWN_CONTACT"),eq(member.getId()),any(),any());
    }
    @Test void unchangedPhoneRetainsItsVerificationWhenEmailChanges(){
        member.setPhoneVerifiedAt(now);service.update(actor,"new@example.test","255712345678","correct");assertThat(member.getPhoneVerifiedAt()).isEqualTo(now);
    }
    @Test void invalidPasswordAndDuplicateContactsCannotMutateTheRecord(){
        assertThatThrownBy(()->service.update(actor,"new@example.test","255712345678","wrong")).hasMessage("profile.contact.passwordInvalid");
        when(members.existsByEmailIgnoreCaseAndIdNot("new@example.test",member.getId())).thenReturn(true);
        assertThatThrownBy(()->service.update(actor,"new@example.test","255712345678","correct")).hasMessage("profile.contact.duplicate");
        assertThat(member.getEmail()).isEqualTo("old@example.test");verify(members,never()).save(any());verifyNoInteractions(audit,otp);
    }
    @Test void foreignScopeAndInactiveAccountsAreDenied(){
        member.setSaccoId("OTHER");assertThatThrownBy(()->service.update(actor,"new@example.test",null,"correct")).isInstanceOf(AccessDeniedException.class);
        member.setSaccoId("I1");member.setStatus(MemberStatus.INACTIVE);assertThatThrownBy(()->service.requestPhoneOtp(actor,"correct")).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(otp,audit);
    }
    @Test void verificationUsesOnlyTheSavedOwnerPhoneAndDoesNotRepeatAfterSuccess(){
        assertThat(service.requestPhoneOtp(actor,"correct")).isTrue();verify(otp).issueClaimOtp("255712345678",member.getId());
        service.verifyPhone(actor,"123456","correct");assertThat(member.getPhoneVerifiedAt()).isEqualTo(now);
        service.verifyPhone(actor,"123456","correct");assertThat(service.requestPhoneOtp(actor,"correct")).isFalse();
        verify(otp,times(1)).consumeClaimOtp("255712345678",member.getId(),"123456");
    }
    @Test void invalidCodeDoesNotCreatePhoneVerificationEvidence(){
        doThrow(new IllegalArgumentException("profile.contact.codeInvalid")).when(otp).consumeClaimOtp(anyString(),any(),anyString());
        assertThatThrownBy(()->service.verifyPhone(actor,"000000","correct")).hasMessage("profile.contact.codeInvalid");assertThat(member.getPhoneVerifiedAt()).isNull();verify(members,never()).save(any());verifyNoInteractions(audit);
    }
}
