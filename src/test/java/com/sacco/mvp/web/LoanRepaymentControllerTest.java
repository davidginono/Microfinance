package com.sacco.mvp.web;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.LoanRepaymentLedgerService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoanRepaymentControllerTest {
    private static final UUID LOAN = UUID.randomUUID();
    private AnnotationConfigWebApplicationContext context;
    private LoanRepaymentLedgerService ledger;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        ledger = context.getBean(LoanRepaymentLedgerService.class);
        when(context.getBean(ApplicationClock.class).today()).thenReturn(LocalDate.of(2026, 10, 1));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void retiredIndexAndPaymentEntryAreUnavailableEvenForExistingStaff() throws Exception {
        mvc.perform(get("/repayments").with(user(actor(true, UserClaim.LOAN_REPAYMENTS_VIEW))))
            .andExpect(status().isNotFound());
        mvc.perform(post("/repayments/loans/" + LOAN + "/payments")
                .with(user(actor(true, UserClaim.LOAN_RECORDING_POST))).with(csrf()))
            .andExpect(status().isNotFound());
        verifyNoInteractions(ledger);
    }

    @Test
    void authenticatedStaffCanOpenScopedRepaymentHistory() throws Exception {
        mvc.perform(get("/repayments/loans/" + LOAN).with(user(actor(true, UserClaim.LOAN_REPAYMENTS_VIEW))))
            .andExpect(status().isOk()).andExpect(view().name("repayments/loan"));
        verify(ledger).view(eq(LOAN), any(), eq(0), eq(0));
    }

    @Test
    void unauthenticatedReceiptCannotBeRead() throws Exception {
        mvc.perform(get("/repayments/loans/" + LOAN + "/receipts/" + UUID.randomUUID()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(ledger);
    }

    @Test
    void reversalRequiresDedicatedPermissionAndCsrf() throws Exception {
        String url = "/repayments/loans/" + LOAN + "/payments/" + UUID.randomUUID() + "/reverse";
        mvc.perform(post(url).param("requestKey", UUID.randomUUID().toString()).param("reason", "Duplicate cash receipt")
                .param("confirmed", "true").with(user(actor(true, UserClaim.LOAN_REPAYMENTS_VIEW))).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(post(url).param("requestKey", UUID.randomUUID().toString()).param("reason", "Duplicate cash receipt")
                .param("confirmed", "true").with(user(actor(true, UserClaim.LOAN_REPAYMENTS_REVERSE))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(ledger);
    }

    private AppUserPrincipal actor(boolean staff, UserClaim claim) {
        return (staff ? new com.sacco.mvp.security.AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1")
            .memberNo("C001").position(Position.MANAGER).memberAccount(false).status(MemberStatus.ACTIVE).staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE).build(),Set.of(claim)) : com.sacco.mvp.config.MvcSecurityTestConfiguration.formerClient(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1")
            .memberNo("C001").position(Position.MANAGER).memberAccount(true).status(MemberStatus.ACTIVE).staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE).build(),Set.of(claim)));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean(name = "access") AccessControlService access() { return new AccessControlService(); }
        @Bean LoanRepaymentLedgerService ledger() { return mock(LoanRepaymentLedgerService.class); }
        @Bean ApplicationClock clock() { return mock(ApplicationClock.class); }
        @Bean LoanRepaymentController controller(LoanRepaymentLedgerService ledger, ApplicationClock clock) {
            return new LoanRepaymentController(ledger, clock, mock(com.sacco.mvp.service.LoanRepaymentReceiptPdfService.class));
        }
        @Bean InternalResourceViewResolver views() { return new InternalResourceViewResolver("/WEB-INF/jsp/", ".jsp"); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(a -> a.anyRequest().authenticated()).build();
        }
    }
}
