package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GuarantorSearchModeViewContractTest {
    private static final Path LOAN_NEW_JSP = Path.of("src/main/webapp/WEB-INF/jsp/app/loan-new.jsp");
    private static final Path SETTINGS_JSP = Path.of("src/main/webapp/WEB-INF/jsp/admin/settings-controls.jsp");

    @Test
    void loginApprovalDefaultsToMemberNumberAndDirectOtpUsesPhoneThenEmail() throws Exception {
        String view = Files.readString(LOAN_NEW_JSP).replace("\r\n", "\n");

        assertThat(view)
            .contains("<option value=\"number\"><spring:message code=\"newloan.guarantors.modeNumber\" /></option>")
            .contains("<option value=\"name\"><spring:message code=\"newloan.guarantors.modeName\" /></option>")
            .contains("LOGIN: [")
            .contains("{ value: \"number\", label: modeNumberLabel, placeholder: numberPlaceholder, hint: numberHint }")
            .contains("{ value: \"name\", label: modeNameLabel, placeholder: namePlaceholder, hint: nameHint }")
            .contains("DIRECT_OTP: [")
            .contains("{ value: \"phone\", label: modePhoneLabel, placeholder: phonePlaceholder, hint: phoneHint }")
            .contains("{ value: \"email\", label: modeEmailLabel, placeholder: emailPlaceholder, hint: emailHint }")
            .contains("const endpoint = approvalMode === \"DIRECT_OTP\" ? \"/app/guarantors/direct-otp/search\" : \"/app/guarantors/search\";");
    }

    @Test
    void selectedGuarantorChipsKeepMemberNumberNameLabel() throws Exception {
        String view = Files.readString(LOAN_NEW_JSP).replace("\r\n", "\n");

        assertThat(view)
            .contains("chip.innerHTML = \"<span>\" + escapeHtml(item.memberNo) + \" - \" + escapeHtml(item.fullName)");
    }

    @Test
    void adminQualificationSettingsExposePortfolioAtRiskDays() throws Exception {
        String view = Files.readString(SETTINGS_JSP).replace("\r\n", "\n");

        assertThat(view)
            .contains("name=\"portfolioAtRiskDays\"")
            .contains("min=\"1\"")
            .contains("max=\"365\"")
            .contains("value=\"${policyPortfolioAtRiskDays}\"");
    }
}
