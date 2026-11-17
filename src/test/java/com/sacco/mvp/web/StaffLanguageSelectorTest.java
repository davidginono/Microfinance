package com.sacco.mvp.web;

import com.sacco.mvp.config.MemberLocaleInterceptor;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.NotificationInboxService;
import com.sacco.mvp.service.UserSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StaffLanguageSelectorTest {
    @Test
    void languageCommandRequiresAuthenticationAndCsrf() {
        new org.springframework.boot.test.context.runner.WebApplicationContextRunner()
            .withUserConfiguration(LanguageSecurity.class).run(context -> {
                var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
                var principal=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).position(Position.MINOR_ADMIN)
                    .status(com.sacco.mvp.domain.MemberStatus.ACTIVE).staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE).build(),Set.of());
                var command=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/staff/settings/language")
                    .param("language","sw").param("returnTo","/staff/dashboard");
                mvc.perform(command.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(principal)))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/staff/settings/language").param("language","sw")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
                mvc.perform(command.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(principal))
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/staff/dashboard"));
            });
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class LanguageSecurity {
        @org.springframework.context.annotation.Bean
        org.springframework.security.web.SecurityFilterChain security(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(requests->requests.anyRequest().authenticated()).build();
        }
        @org.springframework.context.annotation.Bean
        StaffSettingsController controller() {
            var settings=mock(UserSettingsService.class);when(settings.updateLanguage(any(),any())).thenAnswer(call->call.getArgument(1));
            return new StaffSettingsController(mock(NotificationInboxService.class),settings,mock(MemberLocaleInterceptor.class));
        }
    }

    @Test
    void savesOnlyTheSignedInUserPreferenceAndReturnsToTheCurrentFilteredPage() {
        var settings=mock(UserSettingsService.class);var locale=mock(MemberLocaleInterceptor.class);
        var principal=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).position(Position.MINOR_ADMIN).status(com.sacco.mvp.domain.MemberStatus.ACTIVE).staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE).build(),Set.of());
        when(settings.updateLanguage(principal.getMemberId(),"sw")).thenReturn("sw");
        var controller=new StaffSettingsController(mock(NotificationInboxService.class),settings,locale);
        var request=new MockHttpServletRequest();var flash=new RedirectAttributesModelMap();
        assertThat(controller.updateLanguage(principal,"sw","/admin/settings-controls?section=loan#products",request,flash))
            .isEqualTo("redirect:/admin/settings-controls?section=loan#products");
        verify(settings).updateLanguage(principal.getMemberId(),"sw");verify(locale).cacheUserLocale(request,principal.getMemberId(),"sw");
        assertThat(flash.getFlashAttributes()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings={"https://example.com/","//example.com/","/%2fexample.com/","/\\example.com/","/%5cexample.com/","/path\r\nLocation:evil","/%0d%0aevil","javascript:alert(1)","relative",""})
    void unsafeReturnDestinationsUseTheExistingSettingsPage(String destination) {
        var settings=mock(UserSettingsService.class);var locale=mock(MemberLocaleInterceptor.class);
        var principal=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).position(Position.MINOR_ADMIN).status(com.sacco.mvp.domain.MemberStatus.ACTIVE).staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE).build(),Set.of());
        when(settings.updateLanguage(principal.getMemberId(),"en")).thenReturn("en");
        var controller=new StaffSettingsController(mock(NotificationInboxService.class),settings,locale);
        assertThat(controller.updateLanguage(principal,"en",destination,new MockHttpServletRequest(),new RedirectAttributesModelMap()))
            .isEqualTo("redirect:/staff/settings");
    }
}
