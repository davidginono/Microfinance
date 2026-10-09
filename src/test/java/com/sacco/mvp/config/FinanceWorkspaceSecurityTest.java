package com.sacco.mvp.config;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.MemberStatus;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.StaffAccessStatus;
import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig({LoginCsrfAccessDeniedTest.TestConfig.class, FinanceWorkspaceSecurityTest.Endpoints.class})
class FinanceWorkspaceSecurityTest {
    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void clientSessionCannotEnterStaffFinanceEvenWithAccidentallyAssignedClaim() throws Exception {
        for (String path : Set.of("/finance/test-boundary", "/reports/test-boundary")) {
            mvc.perform(get(path).with(user(principal(false, "I1", "B1"))))
                .andExpect(status().isForbidden());
        }
    }

    @Test void staffWithoutInstitutionOrBranchCannotEnterFinance() throws Exception {
        mvc.perform(get("/finance/test-boundary").with(user(principal(true, null, "B1"))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/reports/test-boundary").with(user(principal(true, "I1", " "))))
            .andExpect(status().isForbidden());
    }

    @Test void scopedStaffPassesWorkspaceBoundaryAndMutationsStillRequireCsrf() throws Exception {
        var actor = principal(true, "I1", "B1");
        mvc.perform(get("/finance/test-boundary").with(user(actor))).andExpect(status().isOk());
        mvc.perform(post("/finance/test-boundary").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/test-boundary").with(user(actor)).with(csrf())).andExpect(status().isOk());
    }

    @Test void onlyAccountantsReceiveDirectAccountingAndLoanRecordingDefaults() {
        for (Position role : Position.values()) {
            var financial=UserClaim.defaultClaims(Set.of(role), true).stream().filter(claim ->
                claim.name().startsWith("ACCOUNTING_") || claim.name().startsWith("REPORT_TEMPLATE_")
                    || claim.name().startsWith("LOAN_RECORDING_")).toList();
            if(role==Position.ACCOUNTANT) assertThat(financial).containsExactlyInAnyOrder(
                UserClaim.ACCOUNTING_ACCOUNTS_VIEW,UserClaim.ACCOUNTING_ACCOUNTS_CREATE,UserClaim.ACCOUNTING_ACCOUNTS_UPDATE,
                UserClaim.ACCOUNTING_JOURNALS_VIEW,UserClaim.ACCOUNTING_JOURNALS_CREATE,UserClaim.ACCOUNTING_JOURNALS_REVERSE,
                UserClaim.ACCOUNTING_BUSINESS_VIEW,UserClaim.ACCOUNTING_BUSINESS_CREATE,UserClaim.ACCOUNTING_BUSINESS_REVERSE,
                UserClaim.LOAN_RECORDING_VIEW,UserClaim.LOAN_RECORDING_CREATE,UserClaim.LOAN_RECORDING_DISBURSE,
                UserClaim.LOAN_RECORDING_POST,UserClaim.LOAN_RECORDING_REVERSE,UserClaim.LOAN_RECORDING_EXPORT);
            else assertThat(financial).isEmpty();
        }
    }

    private AppUserPrincipal principal(boolean staff, String institution, String branch) {
        Member member = Member.builder().id(UUID.randomUUID()).memberNo("SECURITY-TEST")
            .saccoId(institution).stationId(branch).fullName("Synthetic Security Test")
            .memberAccount(true).status(MemberStatus.ACTIVE)
            .staffRoles(staff ? Set.of(Position.ACCOUNTANT) : Set.of())
            .staffAccessStatus(staff ? StaffAccessStatus.ACTIVE : StaffAccessStatus.NONE).build();
        return new AppUserPrincipal(member, Set.of(UserClaim.ACCOUNTING_JOURNALS_VIEW), staff);
    }

    @Configuration @EnableWebMvc
    static class Endpoints {
        @Bean BoundaryController boundaryController() { return new BoundaryController(); }
    }

    // Synthetic endpoints isolate the global boundary. Each real feature also enforces action and record scope.
    @RestController
    static class BoundaryController {
        @GetMapping({"/finance/test-boundary", "/reports/test-boundary"}) String get() { return "ok"; }
        @PostMapping("/finance/test-boundary") String post() { return "ok"; }
    }
}
