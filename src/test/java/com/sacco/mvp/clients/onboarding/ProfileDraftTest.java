package com.sacco.mvp.clients.onboarding;

import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.ClientProfile;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileDraftTest {
    final ApplicationClock clock=mock(ApplicationClock.class);
    final OnboardingService service=new OnboardingService(null,null,null,null,null,clock,null,JsonMapper.builder().findAndAddModules().build(),null,null);
    @Test void emptyAndIncompleteProfilesRoundTripWithoutRequiringRegistrationOrConsent() {
        when(clock.today()).thenReturn(LocalDate.of(2026,11,5));
        var profile=new ClientProfile();profile.setCity("Arusha");profile.setBankAccountNumber("12345678");
        var restored=service.decodeProfileDraft(service.encodeProfileDraft(profile));
        assertThat(restored.getCity()).isEqualTo("Arusha");assertThat(restored.getBankAccountNumber()).isEqualTo("12345678");assertThat(restored.getFirstName()).isEmpty();assertThat(restored.getPhone()).isEmpty();
        assertThat(service.decodeProfileDraft(service.encodeProfileDraft(new ClientProfile())).getLastName()).isEmpty();
    }
    @Test void malformedProvidedValuesAreStillRejected() {
        var profile=new ClientProfile();profile.setPhone("123");assertThatThrownBy(()->service.encodeProfileDraft(profile)).hasMessage("kyc.error.phone");
        profile.setPhone("");profile.setCity("x".repeat(256));assertThatThrownBy(()->service.encodeProfileDraft(profile)).hasMessage("kyc.error.city");
    }
    @Test void publicDraftRetainsOnlyEnteredProfileAndScopeWithoutOtpOrAccountCreation() {
        var portal=new com.sacco.mvp.clients.portal.service.ClientPortalService(null,null,null,null,service,clock,null,null,null);
        var form=new com.sacco.mvp.clients.portal.dto.ClientPortalDtos.EntryForm();form.setInstitution("I1");form.setBranch("B1");form.getRegistration().getProfile().setCity("Arusha");
        var draft=portal.registrationDraft(form);assertThat(draft.actor()).isNull();assertThat(draft.institution()).isEqualTo("I1");assertThat(draft.branch()).isEqualTo("B1");assertThat(service.decodeProfileDraft(draft.profile()).getCity()).isEqualTo("Arusha");
        form.setInstitution("x".repeat(256));assertThatThrownBy(()->portal.registrationDraft(form)).isInstanceOf(IllegalArgumentException.class);
    }
}
