package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import com.sacco.mvp.web.ProfileController;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class, StaffSignatureMvcTest.Config.class})
class StaffSignatureMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired StaffSignatureService signatures;
    @Autowired EmployeeProfileService employeeProfile;
    @Autowired ProfileContactService contacts;
    MockMvc mvc; AppUserPrincipal actor;
    @BeforeEach void setup() {
        reset(signatures,employeeProfile,contacts);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        actor = new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1")
            .position(Position.MANAGER).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(), EnumSet.allOf(UserClaim.class), true);
    }
    @Test void requiresCsrfAndUsesSessionOwnerRatherThanSubmittedIds() throws Exception {
        mvc.perform(post("/profile/signature").with(user(actor)).param("fullName", "Amina Juma").param("currentPassword", "password")).andExpect(status().isForbidden());
        verifyNoInteractions(signatures);
        mvc.perform(post("/profile/signature").with(user(actor)).with(csrf()).param("fullName", "Amina Juma")
            .param("currentPassword", "password").param("confirmed", "true").param("memberId", UUID.randomUUID().toString())
            .param("signatureText", "Injected signature")).andExpect(redirectedUrl("/profile"));
        verify(signatures).register(eq(actor), eq("Amina Juma"), eq("password"), eq(true));
    }
    @Test void validationRetainsNameButNeverPasswordInFlash() throws Exception {
        doThrow(new IllegalArgumentException("profile.signature.invalidName")).when(signatures).register(any(), anyString(), anyString(), anyBoolean());
        mvc.perform(post("/profile/signature").with(user(actor)).with(csrf()).param("fullName", "Amina").param("currentPassword", "password"))
            .andExpect(redirectedUrl("/profile")).andExpect(flash().attribute("signatureFullName", "Amina"))
            .andExpect(flash().attribute("signatureError", "Enter two names"))
            .andExpect(result -> org.junit.jupiter.api.Assertions.assertFalse(result.getFlashMap().containsKey("currentPassword")));
    }
    @Test void employeeNumberEditRequiresCsrfAndAlwaysUsesTheSessionOwner()throws Exception {
        mvc.perform(post("/profile/employee-number").with(user(actor)).param("employeeNumber","0001")).andExpect(status().isForbidden());
        verifyNoInteractions(employeeProfile);
        mvc.perform(post("/profile/employee-number").with(user(actor)).with(csrf()).param("employeeNumber","0001").param("memberId",UUID.randomUUID().toString()))
            .andExpect(redirectedUrl("/profile")).andExpect(flash().attribute("message","Saved"));
        verify(employeeProfile).update(actor,"0001");
    }
    @Test void contactChangesRequireCsrfAndIgnoreForgedOwnerFields()throws Exception {
        mvc.perform(post("/profile/contact").with(user(actor)).param("email","new@example.test").param("currentPassword","password")).andExpect(status().isForbidden());
        mvc.perform(post("/profile/contact").with(user(actor)).with(csrf()).param("email","new@example.test").param("phone","255712345678").param("currentPassword","password").param("memberId",UUID.randomUUID().toString())).andExpect(redirectedUrl("/profile"));
        verify(contacts).update(actor,"new@example.test","255712345678","password");
    }
    @Test void phoneVerificationHasAFiniteSessionAttemptBudgetAndRequiresCsrf()throws Exception {
        var session=new org.springframework.mock.web.MockHttpSession();
        doThrow(new IllegalArgumentException("profile.contact.codeInvalid")).when(contacts).verifyPhone(any(),anyString(),anyString());
        mvc.perform(post("/profile/phone/verify").with(user(actor)).param("code","123456").param("currentPassword","password")).andExpect(status().isForbidden());
        for(int i=0;i<6;i++)mvc.perform(post("/profile/phone/verify").session(session).with(user(actor)).with(csrf()).param("code","123456").param("currentPassword","password")).andExpect(redirectedUrl("/profile"));
        verify(contacts,times(5)).verifyPhone(actor,"123456","password");
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean ProfileContactService contacts(){return mock(ProfileContactService.class);}
        @Bean StaffSignatureService signatures() { return mock(StaffSignatureService.class); }
        @Bean EmployeeProfileService employeeProfile() { return mock(EmployeeProfileService.class); }
        @Bean ProfileController profile(StaffSignatureService signatures,EmployeeProfileService employeeProfile,ProfileContactService contacts) {
            var messages = new StaticMessageSource();
            for (var locale : List.of(Locale.ENGLISH, Locale.getDefault())) {
                messages.addMessage("profile.signature.saved", locale, "Saved");
                messages.addMessage("payroll.employeeNumber.saved", locale, "Saved");
                messages.addMessage("profile.signature.invalidName", locale, "Enter two names");
                for(String key:List.of("saved","verified","attempts","codeInvalid"))messages.addMessage("profile.contact."+key,locale,key);
            }
            return new ProfileController(mock(MemberDirectoryService.class), mock(MemberProfileImageService.class), contacts, signatures, messages, employeeProfile);
        }
    }
}
