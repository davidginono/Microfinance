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
    void guarantorSearchShowsInlineFormatVerificationOnlyAfterSearchClick() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/app/loan-new.jsp"));
        String styles = Files.readString(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(source)
            .contains("id=\"guarantorFormatIndicator\"")
            .contains("id=\"guarantorFormatMessage\"")
            .contains("aria-describedby=\"guarantorHint guarantorFormatMessage\"")
            .contains("function validateSearchTerm(showEmpty)")
            .contains("function normalizePhoneSearch(value)")
            .contains("searchInput.setAttribute(\"aria-invalid\", \"true\")")
            .contains("const validation = refreshSearchValidation(true);")
            .contains("const term = validation.term;")
            .contains("return { valid: true, message: \"\", term: raw.toUpperCase() };")
            .contains("if (!result.valid && forceMessage)")
            .contains("searchInput.addEventListener(\"input\", function () {")
            .contains("refreshSearchValidation(false);")
            .doesNotContain("validFourDigitsOrMore")
            .doesNotContain("validFullMemberNo")
            .doesNotContain("if (!result.valid && (hasValue || forceMessage))");

        assertThat(styles)
            .contains(".loan-guarantor-search-spinner > span")
            .contains("@keyframes loan-guarantor-search-pulse")
            .contains("prefers-reduced-motion: reduce");
    }

    @Test
    void selectedGuarantorChipsKeepMemberNumberAndNameLabel() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/app/loan-new.jsp"));

        assertThat(source)
            .contains("${item.memberNo} - ${item.fullName}")
            .contains("<div id=\"selectedGuarantorInputs\">")
            .contains("name=\"guarantorSelections\"")
            .contains("value=\"${fn:escapeXml(not empty item.selectionToken ? item.selectionToken : (not empty item.localMemberId ? item.localMemberId : item.id))}\"")
            .contains("input.name = \"guarantorSelections\"")
            .contains("id=\"selectedGuarantorState\" name=\"guarantorSelectionState\"")
            .contains("selectionStateInput.value = JSON.stringify(selectionTokens);")
            .contains("item.memberNo) + \" - \" + escapeHtml(item.fullName)");
    }

    @Test
    void confirmedSubmitKeepsSendActionAndSelectedGuarantors() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/app/loan-new.jsp"));
        String normalizedSource = source.replace("\r\n", "\n");

        assertThat(normalizedSource)
            .contains("function resolveSubmitButton(event)")
            .contains("form.dataset.pendingFormAction = button.dataset.formAction || \"\";")
            .contains("const formAction = submitButton.dataset.formAction || form.dataset.pendingFormAction || \"SAVE_DRAFT\";")
            .contains("actionInput.value = formAction;")
            .contains("const configuredInitialStep = Number(\"${loanFormInitialStep}\");")
            .contains("window.SaccosLoanForm.activateStep = activateStep;")
            .contains("function submitAction(event)")
            .contains("return form.dataset.pendingFormAction || (actionInput ? actionInput.value : \"\");")
            .contains("function blockForGuarantorSelection(event)")
            .contains("window.SaccosLoanForm?.activateStep?.(3, true);")
            .contains("const action = submitAction(event);")
            .contains("renderHiddenInputs();\n                if (selected.size !== required) {\n                    blockForGuarantorSelection(event);");
    }
}
