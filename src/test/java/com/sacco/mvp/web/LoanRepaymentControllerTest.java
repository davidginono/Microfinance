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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.math.BigDecimal;
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
    void validVerifiedPaymentRedirectsToItsReceipt() throws Exception {
        UUID receipt = UUID.randomUUID();
        when(ledger.post(eq(LOAN), any(), any())).thenReturn(new LoanRepaymentLedgerService.Receipt(
            receipt, LOAN, "100001", "R-1", "PAYMENT", LocalDate.of(2026, 10, 1), null,
            new BigDecimal("20.00"), new BigDecimal("10.00"), new BigDecimal("10.00"), "CASH", "C-1", null, null, null));

        mvc.perform(validPayment().with(user(actor(true, UserClaim.LOAN_REPAYMENTS_CREATE))).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/repayments/loans/" + LOAN + "/receipts/" + receipt));
        verify(ledger).post(eq(LOAN), any(), argThat(c -> c.amount().compareTo(new BigDecimal("20.00")) == 0));
    }

    @Test
    void missingCsrfCannotPost() throws Exception {
        mvc.perform(validPayment().with(user(actor(true, UserClaim.LOAN_REPAYMENTS_CREATE))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(ledger);
    }

    @Test
    void viewPermissionCannotPost() throws Exception {
        mvc.perform(validPayment().with(user(actor(true, UserClaim.LOAN_REPAYMENTS_VIEW))).with(csrf()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(ledger);
    }

    @Test
    void clientLoanPermissionCannotOpenStaffRegistry() throws Exception {
        mvc.perform(get("/repayments").with(user(actor(false, UserClaim.MEMBER_LOANS_VIEW))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(ledger);
    }

    @Test
    void unauthenticatedReceiptCannotBeRead() throws Exception {
        mvc.perform(get("/repayments/loans/" + LOAN + "/receipts/" + UUID.randomUUID()))
            .andExpect(status().isForbidden());
        verifyNoInteractions(ledger);
    }

    @Test
    void paymentMustBeConfirmed() throws Exception {
        mvc.perform(validPayment("20.00", "false")
                .with(user(actor(true, UserClaim.LOAN_REPAYMENTS_CREATE))).with(csrf()))
            .andExpect(status().isOk()).andExpect(view().name("repayments/loan"))
            .andExpect(model().attribute("repaymentError", "repayment.error.validation"));
        verify(ledger, never()).post(any(), any(), any());
    }

    @Test
    void excessiveDecimalPrecisionIsRejectedBeforePosting() throws Exception {
        mvc.perform(validPayment("20.001", "true")
                .with(user(actor(true, UserClaim.LOAN_REPAYMENTS_CREATE))).with(csrf()))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("paymentForm", "amount"));
        verify(ledger, never()).post(any(), any(), any());
    }

    @Test
    void reversalRequiresDedicatedPermissionAndCsrf() throws Exception {
        String url = "/repayments/loans/" + LOAN + "/payments/" + UUID.randomUUID() + "/reverse";
        mvc.perform(post(url).param("requestKey", UUID.randomUUID().toString()).param("reason", "Duplicate cash receipt")
                .param("confirmed", "true").with(user(actor(true, UserClaim.LOAN_REPAYMENTS_CREATE))).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(post(url).param("requestKey", UUID.randomUUID().toString()).param("reason", "Duplicate cash receipt")
                .param("confirmed", "true").with(user(actor(true, UserClaim.LOAN_REPAYMENTS_REVERSE))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(ledger);
    }

    @Test
    void invalidSearchRendersAnErrorWithoutChangingData() throws Exception {
        when(ledger.list(any(), eq("not-a-number"), anyInt()))
            .thenThrow(new IllegalArgumentException("repayment.error.search"));
        mvc.perform(get("/repayments").param("loanNumber", "not-a-number")
                .with(user(actor(true, UserClaim.LOAN_REPAYMENTS_VIEW))))
            .andExpect(status().isOk()).andExpect(view().name("repayments/index"))
            .andExpect(model().attribute("repaymentError", "repayment.error.search"));
    }

    private MockHttpServletRequestBuilder validPayment() {
        return validPayment("20.00", "true");
    }

    private MockHttpServletRequestBuilder validPayment(String amount, String confirmed) {
        return post("/repayments/loans/" + LOAN + "/payments").param("amount", amount)
            .param("paymentDate", "2026-10-01").param("channel", "CASH").param("reference", "C-1")
            .param("requestKey", UUID.randomUUID().toString()).param("confirmed", confirmed);
    }

    private AppUserPrincipal actor(boolean staff, UserClaim claim) {
        return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1")
            .memberNo("C001").position(Position.MANAGER).memberAccount(true).status(MemberStatus.ACTIVE).build(), Set.of(claim), staff);
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
            return new LoanRepaymentController(ledger, clock);
        }
        @Bean InternalResourceViewResolver views() { return new InternalResourceViewResolver("/WEB-INF/jsp/", ".jsp"); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(a -> a.anyRequest().authenticated()).build();
        }
    }
}
