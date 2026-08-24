package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DirectOtpGuarantorSearchViewContractTest {

    @Test
    void loginApprovalDefaultsGuarantorSearchToMemberNumber() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/app/loan-new.jsp"));

        assertThat(source.indexOf("<option value=\"number\">"))
            .isLessThan(source.indexOf("<option value=\"name\">"));
        assertThat(source)
            .contains("placeholder=\"<spring:message code='newloan.guarantors.placeholder' />\"");
    }

    @Test
    void directOtpSwitchesSearchModesAndUsesForesightBackedEndpoint() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/app/loan-new.jsp"));

        assertThat(source)
            .contains("{ value: \"phone\", label: modePhoneLabel")
            .contains("{ value: \"email\", label: modeEmailLabel")
            .contains("\"/app/guarantors/direct-otp/search\"");
    }

    @Test
    void guarantorSearchShowsInlineFormatVerificationBeforeFetch() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/app/loan-new.jsp"));

        assertThat(source)
            .contains("id=\"guarantorFormatIndicator\"")
            .contains("id=\"guarantorFormatMessage\"")
            .contains("aria-describedby=\"guarantorHint guarantorFormatMessage\"")
            .contains("function validateSearchTerm(showEmpty)")
            .contains("function normalizePhoneSearch(value)")
            .contains("searchInput.setAttribute(\"aria-invalid\", \"true\")")
            .contains("const validation = refreshSearchValidation(true);")
            .contains("const term = validation.term;");
    }

    @Test
    void selectedGuarantorChipsKeepMemberNumberAndNameLabel() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/app/loan-new.jsp"));

        assertThat(source)
            .contains("${item.memberNo} - ${item.fullName}")
            .contains("input.name = \"guarantorSelections\"")
            .contains("item.memberNo) + \" - \" + escapeHtml(item.fullName)");
    }
}
