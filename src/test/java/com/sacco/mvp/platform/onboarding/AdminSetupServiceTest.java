package com.sacco.mvp.platform.onboarding;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.platform.onboarding.dto.AdminSetupForm;
import com.sacco.mvp.platform.onboarding.repository.AdminSetupRepository;
import com.sacco.mvp.platform.onboarding.service.AdminSetupService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AdminSetupServiceTest {
    AdminSetupRepository records=mock(AdminSetupRepository.class);
    AuditService audit=mock(AuditService.class);
    BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(4);
    AdminSetupService service=new AdminSetupService(records,passwords,new NameSignatureService(),audit);
    UUID id=AdminSetupService.BOOTSTRAP_ID;
    String temporary="SyntheticTemporarySecret!";
    AppUserPrincipal actor() {
        return new AppUserPrincipal(Member.builder().id(id).memberNo("ADM001").position(Position.ADMIN)
            .staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).platformSetupRequired(true).build(),
            Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE),true);
    }
    AdminSetupForm form() {
        var f=new AdminSetupForm();f.setFullName("Synthetic Test Administrator");f.setEmail("admin@example.invalid");f.setPhone("0712345678");
        f.setCurrentPassword(temporary);f.setPassword("DifferentPermanentSecret!");f.setConfirmPassword(f.getPassword());return f;
    }
    void pending() {when(records.lock(id)).thenReturn(Optional.of(new AdminSetupRepository.Account(passwords.encode(temporary),true)));}
    @Test void bootstrapUsesOnlyUninitializedIdentityAndStoresAnEncodedSecret() {
        when(records.lock(id)).thenReturn(Optional.of(new AdminSetupRepository.Account("!BOOTSTRAP_REQUIRED",true)));
        assertThat(service.provision(temporary)).isTrue();
        var hash=org.mockito.ArgumentCaptor.forClass(String.class);verify(records).provision(eq(id),hash.capture());
        assertThat(passwords.matches(temporary,hash.getValue())).isTrue();
    }
    @Test void restartNeverResetsAnInitializedTemporaryOrPermanentPassword() {
        when(records.lock(id)).thenReturn(Optional.of(new AdminSetupRepository.Account(passwords.encode(temporary),true)));
        assertThat(service.provision("another-initial-secret")).isFalse();verify(records,never()).provision(any(),any());
        when(records.lock(id)).thenReturn(Optional.of(new AdminSetupRepository.Account("permanent",false)));
        assertThat(service.provision("another-initial-secret")).isFalse();
    }
    @Test void noIdentityIsNotCreatedImplicitly() {
        when(records.lock(id)).thenReturn(Optional.empty());assertThat(service.provision(temporary)).isFalse();
    }
    @Test void productionEncoderNeverAuthenticatesTheDisabledBootstrapMarker() {
        var encoder=new com.sacco.mvp.config.SecurityConfig().passwordEncoder();
        assertThat(encoder.matches("!BOOTSTRAP_REQUIRED","!BOOTSTRAP_REQUIRED")).isFalse();
        assertThat(encoder.matches(temporary,encoder.encode(temporary))).isTrue();
    }
    @Test void setupChecksCurrentSecretAndDoesNotStoreOnFailure() {
        pending();var f=form();f.setCurrentPassword("wrong");
        assertThatThrownBy(()->service.complete(actor(),f)).hasMessage("adminSetup.error.currentPassword");
        verify(records,never()).complete(any(),any(),any(),any(),any());
    }
    @Test void setupRejectsReusedMismatchedAndBcryptTruncatedSecrets() {
        pending();var f=form();f.setPassword(temporary);f.setConfirmPassword(temporary);
        assertThatThrownBy(()->service.complete(actor(),f)).hasMessage("adminSetup.error.reuse");
        f.setPassword("NewPermanentSecret!");
        assertThatThrownBy(()->service.complete(actor(),f)).hasMessage("adminSetup.error.confirmPassword");
        f.setPassword("é".repeat(40));f.setConfirmPassword(f.getPassword());
        assertThatThrownBy(()->service.complete(actor(),f)).hasMessage("adminSetup.error.password");
    }
    @Test void setupRejectsDuplicateContactsAndDoesNotCompleteTwice() {
        pending();when(records.contactUsed(any(),any(),any())).thenReturn(true);
        assertThatThrownBy(()->service.complete(actor(),form())).hasMessage("adminSetup.error.contact");
        when(records.lock(id)).thenReturn(Optional.of(new AdminSetupRepository.Account("hash",false)));
        assertThatThrownBy(()->service.complete(actor(),form())).isInstanceOf(AccessDeniedException.class);
    }
    @Test void successfulSetupNormalizesContactsChangesPasswordAndAuditsWithoutSecrets() {
        pending();var f=form();service.complete(actor(),f);
        var hash=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(records).complete(eq(id),eq("SYNTHETIC TEST ADMINISTRATOR"),eq("admin@example.invalid"),eq("255712345678"),hash.capture());
        assertThat(passwords.matches(f.getPassword(),hash.getValue())).isTrue();
        verify(audit).logEvent(eq("PLATFORM_ADMIN"),eq(id),eq("INITIAL_SETUP"),eq(id),eq(AuditEventStatus.SUCCESS),anyString(),anyString(),anyString(),isNull(),isNull(),eq(Map.of()));
    }
    @Test void otherRolesMissingClaimAndFinishedSessionsCannotSetup() {
        var staff=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).build(),Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE),true);
        assertThatThrownBy(()->service.authorize(staff)).isInstanceOf(AccessDeniedException.class);
        var root=Member.builder().id(id).position(Position.ADMIN).platformSetupRequired(true).build();
        assertThatThrownBy(()->service.authorize(new AppUserPrincipal(root,Set.of(),true))).isInstanceOf(AccessDeniedException.class);
        root.setPlatformSetupRequired(false);
        assertThatThrownBy(()->service.authorize(new AppUserPrincipal(root,Set.of(UserClaim.PLATFORM_SETTINGS_UPDATE),true))).isInstanceOf(AccessDeniedException.class);
    }
}
