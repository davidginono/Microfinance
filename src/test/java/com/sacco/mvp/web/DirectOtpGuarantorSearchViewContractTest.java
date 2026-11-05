package com.sacco.mvp.web;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DirectOtpGuarantorSearchViewContractTest {

    @Test
    void loginApprovalDefaultsGuarantorSearchToMemberNumber() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/client-applications/borrower-guarantors.jspf"));

        assertThat(source.indexOf("<option value=\"number\">"))
            .isLessThan(source.indexOf("<option value=\"name\">"));
        assertThat(source)
            .contains("placeholder=\"<spring:message code='newloan.guarantors.placeholder' />\"");
    }

    @Test
    void directOtpSwitchesSearchModesAndUsesLocalDirectoryEndpoint() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/client-applications/borrower-guarantors.jspf"));

        assertThat(source)
            .contains("{ value: \"phone\", label: modePhoneLabel")
            .contains("{ value: \"email\", label: modeEmailLabel")
            .contains("\"/app/guarantors/direct-otp/search\"");
    }

    @Test
    void directOtpSearchShowsMemberDirectoryOutageInsteadOfNoMatches() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/client-applications/borrower-guarantors.jspf"));

        assertThat(source)
            .contains("const msgMemberDirectoryUnavailable")
            .contains("function showResults(items, emptyMessage, emptyIsError)")
            .contains("const unavailableMessage = approvalMode === \"DIRECT_OTP\" ? msgMemberDirectoryUnavailable : msgUnableSearchGuarantors;")
            .contains("const message = payload.message || unavailableMessage;")
            .contains("showResults([], message, true)")
            .contains("empty.textContent = emptyMessage || msgNoMatches;");
    }

    @Test
    void guarantorSearchShowsInlineFormatVerificationOnlyAfterSearchClick() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/client-applications/borrower-guarantors.jspf"));
        String styles = Files.readString(Path.of("src/main/resources/static/css/console-components.css"));

        assertThat(source)
            .contains("id=\"guarantorFormatIndicator\"")
            .contains("id=\"guarantorFormatMessage\"")
            .contains("aria-describedby=\"guarantorHint guarantorFormatMessage\"")
            .contains("function validateSearchTerm(showEmpty)")
            .contains("function normalizePhoneSearch(value)")
            .contains("searchInput.setAttribute(\"aria-invalid\", \"true\")")
            .contains("const validation = refreshSearchValidation(false);")
            .contains("refreshSearchValidation(true); searchBinding.run();")
            .contains("const term = validation.term;")
            .contains("return { valid: true, message: \"\", term: raw.toUpperCase() };")
            .contains("if (!result.valid && forceMessage)")
            .contains("window.AppSearch.bind(searchInput, () => void runSearch(), () => {")
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
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/client-applications/borrower-guarantors.jspf"));

        assertThat(source)
            .contains("<c:out value=\"${item.memberNo}\"/> - <c:out value=\"${item.fullName}\"/>")
            .contains("<div id=\"selectedGuarantorInputs\">")
            .contains("name=\"guarantorSelections\"")
            .contains("value=\"${fn:escapeXml(not empty item.localMemberId ? item.localMemberId : (not empty item.selectionToken ? item.selectionToken : item.id))}\"")
            .contains("input.name = \"guarantorSelections\"")
            .contains("id=\"selectedGuarantorState\" name=\"guarantorSelectionState\"")
            .contains("function selectionPayloadForItem(item)")
            .contains("selectionStateInput.value = JSON.stringify(selectionPayloads);")
            .contains("function itemFromChip(chip)")
            .contains("function syncSelectedFromRenderedChips()")
            .contains("chip.dataset.selectionKey = selectionKey || \"\";")
            .contains("chip.dataset.selectionToken = selectionToken || \"\";")
            .contains("chip.dataset.source = item.source || \"LMS\";")
            .contains("input.value = JSON.stringify(payload);")
            .contains("syncSelectedFromRenderedChips();")
            .contains("hiddenInputs.innerHTML = \"\";")
            .contains("item.memberNo) + \" - \" + escapeHtml(item.fullName)");
    }

    @Test
    void confirmedSubmitKeepsSendActionAndSelectedGuarantors() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/client-applications/borrower-guarantors.jspf"));
        String form = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/client-applications/form.jsp"));
        String script = Files.readString(Path.of("src/main/resources/static/js/client-applications.js"));

        assertThat(form)
            .contains("name=\"action\" value=\"SEND_TO_GUARANTORS\"")
            .contains("include file=\"borrower-guarantors.jspf\"")
            .contains("include file=\"../fragments/confirm-modal.jspf\"");
        assertThat(script)
            .contains("event.submitter?.value !== 'SEND_TO_GUARANTORS'")
            .contains("form.dataset.confirmMessage = form.dataset.submitConfirmation");
        assertThat(source.replace("\r\n", "\n"))
            .contains("function submitAction(event)")
            .contains("return submitter.dataset.formAction;")
            .contains("function blockForGuarantorSelection(event)")
            .contains("form.querySelector('[data-application-tab=\"guarantors\"]').click();")
            .contains("const action = submitAction(event);")
            .contains("renderHiddenInputs();\n                if (selected.size !== required) {\n                    blockForGuarantorSelection(event);");
    }
}
