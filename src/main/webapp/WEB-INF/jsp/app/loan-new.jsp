<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>
<style>
    @keyframes otp-pop {
        0% { transform: translateY(4px) scale(0.82); opacity: 0; }
        100% { transform: translateY(0) scale(1); opacity: 1; }
    }

    .otp-checkmark-pop {
        animation: otp-pop 180ms ease-out;
    }

    .loan-action-row {
        display: flex;
        flex-wrap: wrap;
        justify-content: flex-end;
        gap: 0.75rem;
        padding-top: 0.5rem;
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="newloan.breadcrumb" /></p>
    <h1 class="erp-page-title"><spring:message code="newloan.title" />: <c:out value="${loanProductName}" /></h1>
    <p class="erp-page-subtitle"><c:out value="${loanProductDescription}" /></p>
</div>
<div class="mb-4 erp-section-muted">
    <h5 class="erp-panel-title"><spring:message code="newloan.eligibilityGuide.title" /></h5>
    <div class="mt-3 grid gap-3 md:grid-cols-2">
        <div class="erp-section">
            <div class="flex flex-wrap items-center gap-2">
                <p class="font-semibold text-slate-800"><spring:message code="newloan.applicationRules.title" /></p>
                <span id="eligibilityExternalInlineStatus" class="inline-flex items-center gap-2 text-xs font-medium text-slate-500">
                    <span id="eligibilityExternalSpinner" class="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-slate-300 border-t-sacco-blue"></span>
                    <span id="eligibilityExternalStatusText"><spring:message code="newloan.applicationRules.loading" /></span>
                </span>
            </div>
            <ul class="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-700">
                <li><spring:message code="newloan.applicationRules.savings" /> <strong id="eligibilitySavingsValue">${savingsLabel}</strong></li>
                <li><spring:message code="newloan.applicationRules.maximumAllowed" /> <strong id="eligibilityMaxAllowedValue">${maxAllowedLabel}</strong></li>
                <li><spring:message code="newloan.applicationRules.amountRange" /> <strong>${minimumAmountLabel}</strong> to <strong>${maximumAmountLabel}</strong></li>
            </ul>
        </div>
        <div class="erp-section">
            <p class="font-semibold text-slate-800"><spring:message code="newloan.beforeSubmit.title" /></p>
            <ul class="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-700">
                <li><spring:message code="newloan.beforeSubmit.item1" /></li>
                <li><spring:message code="newloan.beforeSubmit.item2" /></li>
                <li><spring:message code="newloan.beforeSubmit.item3" /></li>
                <li><spring:message code="newloan.beforeSubmit.item4" arguments="${requiredGuarantors}" /></li>
                <li><spring:message code="newloan.beforeSubmit.item5" arguments="${product.minimumRepaymentMonths},${product.maxRepaymentMonths}" /></li>
            </ul>
        </div>
    </div>
</div>

<form id="loanApplicationForm" method="post" action="/app/loan-applications" enctype="multipart/form-data" class="erp-form-wrap space-y-5">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    <input type="hidden" name="loanType" value="${loanType}" />
    <input type="hidden" id="loanFormAction" name="action" value="SAVE_DRAFT" />
    <input type="hidden" id="financialSnapshotJson" name="financialSnapshotJson" value="${fn:escapeXml(formValues['financialSnapshotJson'])}" />
    <input type="hidden" id="topUpLoanId" name="topUpLoanId" value="${topUpLoanId}" />
    <c:if test="${not empty formValues['applicationId']}">
        <input type="hidden" name="applicationId" value="${formValues['applicationId']}" />
    </c:if>

    <c:if test="${not empty topUpSourceLoan}">
        <div class="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            This application is being created as a loan top-up from application <strong>${topUpSourceLoan.applicationNumber}</strong><c:if test="${not empty topUpSourceLoan.loanId}"> (loan ${topUpSourceLoan.loanId})</c:if>.
        </div>
    </c:if>

    <div class="grid gap-4 md:grid-cols-2">
        <div>
            <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="loan.amount" /> (TSh)</label>
            <input id="loanAmountInput" type="hidden" name="amount" value="${formValues['amount']}" />
            <input id="loanAmountDisplay" type="text" inputmode="decimal" autocomplete="off"
                   value="${formValues['amount']}"
                   placeholder="100,000.00"
                   data-min-amount="${product.minimumAmount}"
                   data-max-amount="${product.maximumAmount}"
                   class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                   required />
            <p class="mt-1 text-xs text-slate-500"><spring:message code="newloan.allowedRange" /> ${minimumAmountLabel} to ${maximumAmountLabel}</p>
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="newloan.tenor.label" /></label>
            <input id="tenorInput" type="number" name="tenorMonths"
                   min="${product.minimumRepaymentMonths}"
                   max="${product.maxRepaymentMonths}"
                   value="${formValues['tenorMonths']}"
                   placeholder="12"
                   class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                   required />
            <p class="mt-1 text-xs text-slate-500"><spring:message code="newloan.allowedTenureRange" /> ${product.minimumRepaymentMonths} to ${product.maxRepaymentMonths} month(s)</p>
        </div>
    </div>

    <div class="erp-section-muted">
        <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="newloan.purpose.label" /></label>
        <textarea name="purpose"
                  rows="4"
                  class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                  placeholder="<spring:message code='newloan.purpose.placeholder' />">${formValues['purpose']}</textarea>
        <p class="mt-2 text-sm text-slate-500"><spring:message code="newloan.purpose.helper" /></p>
    </div>

    <c:forEach items="${formModel.fields}" var="field">
        <c:set var="fieldValue" value="${formValues[field.name]}" />
        <div>
            <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="${field.labelKey}" text="${field.name}" /></label>
            <c:choose>
                <c:when test="${field.type eq 'select'}">
                    <select class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                            name="${field.name}"
                            <c:if test="${field.required}">required</c:if>>
                        <c:forEach items="${field.enumOptions}" var="opt">
                            <option value="${opt}" <c:if test="${fieldValue eq opt}">selected</c:if>>${opt}</option>
                        </c:forEach>
                    </select>
                </c:when>
                <c:when test="${field.type eq 'number'}">
                    <input class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                           type="number" name="${field.name}" step="0.01"
                           value="${fieldValue}" placeholder="e.g. 100000"
                           <c:if test="${field.required}">required</c:if> />
                </c:when>
                <c:when test="${field.type eq 'textarea'}">
                    <textarea class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                              name="${field.name}"
                              placeholder="Enter ${field.name}"
                              <c:if test="${field.required}">required</c:if>>${fieldValue}</textarea>
                </c:when>
                <c:otherwise>
                    <input class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                           type="text" name="${field.name}"
                           value="${fieldValue}" placeholder="Enter ${field.name}"
                           <c:if test="${field.required}">required</c:if> />
                </c:otherwise>
            </c:choose>
        </div>
    </c:forEach>

    <div class="erp-section-muted">
        <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <h5 class="erp-panel-title"><spring:message code="newloan.loanDetails.title" /></h5>
                    <p class="text-sm text-slate-500"><spring:message code="newloan.loanDetails.helper" arguments="${annualInterestPercentLabel}" /></p>
                </div>
            <button id="loadFinancialDetailsButton" type="button" class="app-btn btn-primary">
                <spring:message code="newloan.loanDetails.button" />
            </button>
        </div>
        <div id="financialFeedback" data-auto-scroll-message="true" class="mt-3 hidden rounded-lg border px-4 py-3 text-sm"></div>
        <div id="financialLoading" class="mt-3 hidden erp-section text-sm text-slate-600">
            <div class="flex items-center gap-3">
                <span class="inline-flex h-3 w-3 animate-pulse rounded-full bg-sacco-blue"></span>
                <spring:message code="newloan.loanDetails.loading" />
            </div>
        </div>
        <div id="financialPreviewCard" class="<c:if test='${empty financialSnapshotDisplay}'>hidden </c:if>mt-4 erp-table-wrap overflow-x-auto">
            <table class="erp-table">
                <thead>
                <tr>
                    <th><spring:message code="newloan.table.section" /></th>
                    <th><spring:message code="newloan.table.value" /></th>
                </tr>
                </thead>
                <tbody id="financialPreviewBody">
                <c:forEach items="${financialSnapshotDisplay}" var="entry">
                    <tr>
                        <td class="px-3 py-2 font-medium text-slate-700">${entry.key}</td>
                        <td class="px-3 py-2">${entry.value}</td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
    </div>

    <c:if test="${requiredGuarantors gt 0}">
        <div class="erp-section-muted">
            <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
                <div>
                    <h5 class="erp-panel-title"><spring:message code="newloan.guarantors.title" arguments="${requiredGuarantors}" /></h5>
                    <p class="text-sm text-slate-500"><spring:message code="newloan.guarantors.help" /></p>
                </div>
                <span id="guarantorSelectedCount" class="rounded-full bg-sacco-blue/10 px-3 py-1 text-sm font-semibold text-sacco-blue">
                    <spring:message code="newloan.guarantors.counter" arguments="${requiredGuarantors}" />
                </span>
            </div>

            <div class="relative flex gap-2">
                <input id="guarantorSearch" type="text" autocomplete="off" placeholder="<spring:message code='newloan.guarantors.placeholder' />"
                       class="w-full rounded-lg border border-slate-300 bg-white px-3 py-3 text-sm focus:border-sacco-blue focus:outline-none" />
                <button id="guarantorSearchButton" type="button" class="app-btn btn-primary shrink-0"><spring:message code="common.search" /></button>
                <div id="guarantorDropdown" class="absolute left-0 right-0 z-20 mt-2 hidden max-h-52 overflow-y-auto rounded border border-slate-200 bg-white shadow-lg"></div>
            </div>
            <p id="guarantorHint" class="mt-2 text-sm text-slate-500"><spring:message code="newloan.guarantors.hint" /></p>

            <div id="selectedGuarantors" class="mt-3 flex flex-wrap gap-2">
                <c:forEach items="${selectedGuarantorItems}" var="item">
                    <button type="button"
                            class="selected-guarantor-chip inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700"
                            data-id="${item.id}"
                            data-member-no="${item.memberNo}"
                            data-full-name="${item.fullName}">
                        <span>${item.memberNo} - ${item.fullName}</span>
                        <span class="text-slate-400">x</span>
                    </button>
                </c:forEach>
            </div>
            <div id="selectedGuarantorInputs"></div>
        </div>
    </c:if>

    <div class="erp-section-muted">
        <div class="mb-2">
            <h5 class="erp-panel-title"><spring:message code="newloan.attachments.title" /></h5>
            <p class="text-sm text-slate-500"><spring:message code="newloan.attachments.helper" /></p>
        </div>
        <input type="file" name="attachments" multiple class="w-full rounded-lg border border-dashed border-slate-300 bg-white px-3 py-3 text-sm text-slate-700" />
        <p class="mt-2 text-sm text-slate-500"><spring:message code="newloan.attachments.note" /></p>
    </div>

    <div class="erp-section-muted">
        <h5 class="erp-panel-title"><spring:message code="newloan.declaration.title" /></h5>
        <div class="mt-3 space-y-3 rounded-lg border border-slate-200 bg-white px-4 py-4 text-sm leading-7 text-slate-700">
            <p><spring:message code="newloan.declaration.text" />
                <strong>
                    <c:choose>
                        <c:when test="${not empty currentMember and not empty currentMember.fullName}">${currentMember.fullName}</c:when>
                        <c:otherwise><spring:message code="newloan.declaration.applicantFallback" /></c:otherwise>
                    </c:choose>
                </strong><spring:message code="newloan.declaration.textTail" />
            </p>
            <div class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4">
                <c:choose>
                    <c:when test="${empty formValues['applicationId']}">
                        <div>
                            <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="newloan.otp.title" /></div>
                            <p class="mt-2 text-sm text-slate-600"><spring:message code="newloan.otp.draftFirst" /></p>
                        </div>
                    </c:when>
                    <c:when test="${requiredGuarantors gt 0}">
                        <div>
                            <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="newloan.otp.title" /></div>
                            <p class="mt-2 text-sm text-slate-600"><spring:message code="newloan.otp.afterGuarantors" /></p>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="flex flex-wrap items-center justify-between gap-3">
                            <div>
                                <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="newloan.otp.title" /></div>
                                <p class="mt-2 text-sm text-slate-600"><spring:message code="newloan.otp.requestBeforeSending" /></p>
                                <c:if test="${empty savedSignatureText}">
                                    <p class="mt-2 text-sm text-rose-600"><spring:message code="newloan.otp.addSignature" /></p>
                                </c:if>
                            </div>
                            <button id="requestApplicantSignatureOtpButton" type="button" class="app-btn btn-primary otp-request-button inline-flex items-center justify-center gap-2">
                                <span class="otp-button-spinner hidden"></span>
                                <span class="otp-button-label"><spring:message code="newloan.otp.sendCode" /></span>
                            </button>
                        </div>
                        <div id="applicantSignatureOtpFeedback" data-auto-scroll-message="true" class="mt-3 hidden rounded-lg border px-4 py-3 text-sm"></div>
                        <div class="mt-3">
                            <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="newloan.otp.codeLabel" /></label>
                            <input type="text" name="applicantSignatureOtpCode" id="applicantSignatureOtpCode"
                                   inputmode="numeric" maxlength="6" autocomplete="one-time-code"
                                   class="w-full rounded-lg border border-slate-300 px-3 py-3 tracking-[0.3em] focus:border-sacco-blue focus:outline-none"
                                   placeholder="123456" />
                            <p class="mt-2 text-sm text-slate-500"><spring:message code="newloan.otp.codeHelp" /></p>
                            <div id="applicantSignatureOtpLiveStatus" class="mt-3 hidden items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
                                <span data-otp-spinner class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-sacco-blue"></span>
                                <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                    <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                                </svg>
                                <span data-otp-text><spring:message code="newloan.otp.checkingCode" /></span>
                            </div>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </div>

    <div class="loan-action-row">
        <button class="app-btn btn-neutral px-5 py-3 text-sm"
                type="submit"
                data-form-action="SAVE_DRAFT">
            <spring:message code="common.savedraft" />
        </button>
        <c:if test="${not empty formValues['applicationId']}">
            <c:choose>
                <c:when test="${requiredGuarantors gt 0}">
                    <button class="app-btn btn-approve px-5 py-3 text-sm"
                            type="submit"
                            data-form-action="SEND_TO_GUARANTORS">
                        <spring:message code="newloan.actions.sendToGuarantors" />
                    </button>
                </c:when>
                <c:otherwise>
                    <button class="app-btn btn-approve px-5 py-3 text-sm"
                            type="submit"
                            data-form-action="SEND_TO_GUARANTORS">
                        <spring:message code="common.submit" />
                    </button>
                </c:otherwise>
            </c:choose>
        </c:if>
    </div>
</form>

<script>
    (function () {
        const form = document.getElementById("loanApplicationForm");
        const csrfInput = form.querySelector("input[name='${_csrf.parameterName}']");
        const amountInput = document.getElementById("loanAmountInput");
        const amountDisplayInput = document.getElementById("loanAmountDisplay");
        const tenorInput = document.getElementById("tenorInput");
        const actionInput = document.getElementById("loanFormAction");
        const financialButton = document.getElementById("loadFinancialDetailsButton");
        const financialFeedback = document.getElementById("financialFeedback");
        const financialLoading = document.getElementById("financialLoading");
        const financialCard = document.getElementById("financialPreviewCard");
        const financialBody = document.getElementById("financialPreviewBody");
        const financialSnapshotInput = document.getElementById("financialSnapshotJson");
        const topUpLoanIdInput = document.getElementById("topUpLoanId");
        const signatureOtpButton = document.getElementById("requestApplicantSignatureOtpButton");
        const signatureOtpFeedback = document.getElementById("applicantSignatureOtpFeedback");
        const applicantSignatureOtpInput = document.getElementById("applicantSignatureOtpCode");
        const applicantSignatureOtpLiveStatus = document.getElementById("applicantSignatureOtpLiveStatus");
        const finalSubmitButton = form.querySelector("button[type='submit'][data-form-action='SEND_TO_GUARANTORS']") || form.querySelector("button[type='submit']");
        const eligibilitySavingsValue = document.getElementById("eligibilitySavingsValue");
        const eligibilityMaxAllowedValue = document.getElementById("eligibilityMaxAllowedValue");
        const eligibilityExternalStatus = document.getElementById("eligibilityExternalInlineStatus");
        const eligibilityExternalSpinner = document.getElementById("eligibilityExternalSpinner");
        const eligibilityExternalStatusText = document.getElementById("eligibilityExternalStatusText");
        const msgCheckingCode = "<spring:message code='newloan.js.checkingCode' />";
        const msgVerifyingCode = "<spring:message code='newloan.js.verifyingCode' />";
        const msgVerified = "<spring:message code='newloan.js.verified' />";
        const msgInvalidOtp = "<spring:message code='newloan.js.invalidOtp' />";
        const msgLoadingStatuses = "<spring:message code='newloan.js.loadingStatuses' />";
        const msgUnableLoadSavings = "<spring:message code='newloan.js.unableLoadSavings' />";
        const msgStatusesLoaded = "<spring:message code='newloan.js.statusesLoaded' />";
        const msgUnableLoadOfficialDetails = "<spring:message code='newloan.js.unableLoadOfficialDetails' />";
        const msgSendOtp = "<spring:message code='newloan.js.sendOtp' />";
        const msgSending = "<spring:message code='newloan.js.sending' />";
        const msgOtpSent = "<spring:message code='newloan.js.otpSent' />";
        const msgSelectGuarantorsDraft = "<spring:message code='newloan.js.selectGuarantorsDraft' />";
        const msgLoanAmountChanged = "<spring:message code='newloan.js.loanAmountChanged' />";
        const msgLoadFinancialDetails = "<spring:message code='newloan.loanDetails.loading' />";
        const msgUnableSendOtp = "<spring:message code='newloan.js.unableSendOtp' />";
        const msgOtpEmailSent = "<spring:message code='newloan.js.otpEmailSent' />";
        const msgInvalidOtpCode = "<spring:message code='newloan.js.invalidOtp' />";

        function normalizeMoneyInput(value) {
            const cleaned = (value || "").replace(/,/g, "").replace(/[^\d.]/g, "");
            if (!cleaned) {
                return "";
            }
            const parts = cleaned.split(".");
            const integerPart = parts.shift() || "";
            const decimalPart = parts.join("").slice(0, 2);
            return decimalPart ? integerPart + "." + decimalPart : integerPart;
        }

        function formatMoneyInputValue(value) {
            const normalized = normalizeMoneyInput(value);
            if (!normalized) {
                return "";
            }
            const pieces = normalized.split(".");
            const whole = pieces[0].replace(/^0+(?=\d)/, "") || "0";
            const withCommas = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
            return pieces.length > 1 ? withCommas + "." + pieces[1] : withCommas;
        }

        function syncAmountInput() {
            const normalized = normalizeMoneyInput(amountDisplayInput.value);
            amountInput.value = normalized;
            amountDisplayInput.value = formatMoneyInputValue(normalized);
        }

        function showFinancialFeedback(type, text) {
            financialFeedback.classList.add("hidden");
            financialFeedback.textContent = "";
            window.showToast?.(type === "success" ? "success" : "error", text);
        }

        async function readJsonErrorMessage(response, fallbackMessage) {
            const contentType = response.headers.get("content-type") || "";
            if (contentType.includes("application/json")) {
                try {
                    const payload = await response.json();
                    return payload && payload.message ? payload.message : fallbackMessage;
                } catch (error) {
                    return fallbackMessage;
                }
            }
            try {
                const text = await response.text();
                if (text) {
                    const match = text.match(/<title>(.*?)<\/title>/i);
                    if (match && match[1]) {
                        return match[1];
                    }
                }
            } catch (error) {
                // Ignore response parsing issues and use the fallback message.
            }
            return fallbackMessage;
        }

        function resetFinancialPreview() {
            if (!financialSnapshotInput.value) {
                return;
            }
            financialSnapshotInput.value = "";
            financialCard.classList.add("hidden");
            showFinancialFeedback("error", msgLoanAmountChanged);
        }

        function showSignatureOtpFeedback(type, text) {
            if (!signatureOtpFeedback) {
                return;
            }
            signatureOtpFeedback.textContent = text;
            signatureOtpFeedback.classList.remove(
                "hidden",
                "border-emerald-200",
                "bg-emerald-50",
                "text-emerald-700",
                "border-sacco-brown/30",
                "bg-[#f7efe9]",
                "text-sacco-brown"
            );
            if (type === "success") {
                signatureOtpFeedback.classList.add("border-emerald-200", "bg-emerald-50", "text-emerald-700");
            } else {
                signatureOtpFeedback.classList.add("border-sacco-brown/30", "bg-[#f7efe9]", "text-sacco-brown");
            }
        }

        function setOtpButtonState(button, state, idleLabel, loadingLabel, sentLabel) {
            if (!button) {
                return;
            }
            const spinner = button.querySelector(".otp-button-spinner");
            const label = button.querySelector(".otp-button-label");
            const loading = state === "loading";
            const sent = state === "sent";
            if (spinner) {
                spinner.classList.toggle("hidden", !loading);
            }
            if (label) {
                label.textContent = loading ? loadingLabel : (sent ? sentLabel : idleLabel);
            }
            button.disabled = loading || sent;
            button.classList.toggle("is-loading", loading);
            button.classList.toggle("is-sent", sent);
        }

        function bindOtpLiveStatus(otpInput, statusBox, proceedButton, verifyOtp) {
            if (!otpInput || !statusBox) {
                return {
                    markRequested: function () {},
                    reset: function () {}
                };
            }
            const spinner = statusBox.querySelector("[data-otp-spinner]");
            const tick = statusBox.querySelector("[data-otp-tick]");
            const text = statusBox.querySelector("[data-otp-text]");
            let otpRequested = Boolean((otpInput.value || "").trim());
            let verifiedCode = "";
            let activeVerification = 0;

            function setProceedEnabled(enabled) {
                if (!proceedButton) {
                    return;
                }
                proceedButton.disabled = !enabled;
                proceedButton.classList.toggle("action-button-disabled", !enabled);
            }

            function renderHidden() {
                statusBox.classList.add("hidden");
                statusBox.classList.remove("flex", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-rose-200", "bg-rose-50", "text-rose-700");
                statusBox.classList.add("border-slate-200", "bg-white", "text-slate-600");
                spinner.classList.remove("hidden");
                tick.classList.add("hidden");
                text.textContent = msgCheckingCode;
            }

            function renderPending() {
                statusBox.classList.remove("hidden", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-rose-200", "bg-rose-50", "text-rose-700");
                statusBox.classList.add("flex", "border-slate-200", "bg-white", "text-slate-600");
                spinner.classList.remove("hidden");
                tick.classList.add("hidden");
                text.textContent = msgVerifyingCode;
            }

            function renderVerified() {
                statusBox.classList.remove("hidden", "border-slate-200", "bg-white", "text-slate-600", "border-rose-200", "bg-rose-50", "text-rose-700");
                statusBox.classList.add("flex", "border-emerald-200", "bg-emerald-50", "text-emerald-700");
                spinner.classList.add("hidden");
                tick.classList.remove("hidden");
                tick.classList.remove("otp-checkmark-pop");
                void tick.offsetWidth;
                tick.classList.add("otp-checkmark-pop");
                text.textContent = msgVerified;
            }

            function renderInvalid(message) {
                statusBox.classList.remove("hidden", "border-slate-200", "bg-white", "text-slate-600", "border-emerald-200", "bg-emerald-50", "text-emerald-700");
                statusBox.classList.add("flex", "border-rose-200", "bg-rose-50", "text-rose-700");
                spinner.classList.add("hidden");
                tick.classList.add("hidden");
                text.textContent = message || msgInvalidOtp;
            }

            function render() {
                otpInput.value = (otpInput.value || "").replace(/\D/g, "").slice(0, 6);
                const code = otpInput.value;
                const ready = /^\d{6}$/.test(code);
                if (!otpRequested || !otpInput.value) {
                    activeVerification += 1;
                    verifiedCode = "";
                    setProceedEnabled(false);
                    renderHidden();
                    return;
                }
                if (!ready) {
                    activeVerification += 1;
                    verifiedCode = "";
                    setProceedEnabled(false);
                    renderHidden();
                    return;
                }
                if (verifiedCode === code) {
                    setProceedEnabled(true);
                    renderVerified();
                    return;
                }

                const requestId = ++activeVerification;
                verifiedCode = "";
                setProceedEnabled(false);
                renderPending();
                Promise.resolve(verifyOtp(code))
                    .then(function () {
                        if (requestId !== activeVerification || otpInput.value !== code) {
                            return;
                        }
                        verifiedCode = code;
                        renderVerified();
                        setProceedEnabled(true);
                        if (proceedButton) {
                            try {
                                proceedButton.focus({ preventScroll: true });
                            } catch (ignored) {
                                proceedButton.focus();
                            }
                        }
                    })
                    .catch(function (error) {
                        if (requestId !== activeVerification || otpInput.value !== code) {
                            return;
                        }
                        verifiedCode = "";
                        setProceedEnabled(false);
                        renderInvalid(error && error.message ? error.message : msgInvalidOtp);
                    });
            }

            otpInput.addEventListener("input", render);
            render();
            return {
                markRequested: function () {
                    otpRequested = true;
                    verifiedCode = "";
                    setProceedEnabled(false);
                    render();
                },
                reset: function () {
                    otpRequested = false;
                    activeVerification += 1;
                    verifiedCode = "";
                    setProceedEnabled(false);
                    render();
                }
            };
        }

        async function loadExternalEligibilitySummary() {
            if (!eligibilityExternalStatus) {
                return;
            }
            if (eligibilityExternalSpinner) {
                eligibilityExternalSpinner.classList.remove("hidden");
            }
            if (eligibilityExternalStatusText) {
                eligibilityExternalStatusText.textContent = msgLoadingStatuses;
            }
            eligibilityExternalStatus.classList.remove("text-emerald-600", "text-rose-600");
            eligibilityExternalStatus.classList.add("text-slate-500");
            try {
                const response = await fetch("/app/loan-applications/external-eligibility-summary?loanType=${loanType}", {
                    headers: {
                        "Accept": "application/json",
                        "X-Requested-With": "XMLHttpRequest"
                    }
                });
                if (!response.ok) {
                    throw new Error(msgUnableLoadSavings);
                }
                const payload = await response.json();
                if (payload.savingsLabel) {
                    eligibilitySavingsValue.textContent = payload.savingsLabel;
                }
                if (payload.maxAllowedLabel) {
                    eligibilityMaxAllowedValue.textContent = payload.maxAllowedLabel;
                }
                if (eligibilityExternalSpinner) {
                    eligibilityExternalSpinner.classList.add("hidden");
                }
                if (eligibilityExternalStatusText) {
                    eligibilityExternalStatusText.textContent = msgStatusesLoaded;
                }
                eligibilityExternalStatus.classList.remove("text-rose-600");
                eligibilityExternalStatus.classList.remove("text-slate-500");
                eligibilityExternalStatus.classList.add("text-emerald-600");
            } catch (error) {
                if (eligibilityExternalSpinner) {
                    eligibilityExternalSpinner.classList.add("hidden");
                }
                if (eligibilityExternalStatusText) {
                    eligibilityExternalStatusText.textContent = error.message || msgUnableLoadSavings;
                }
                eligibilityExternalStatus.classList.remove("text-emerald-600");
                eligibilityExternalStatus.classList.remove("text-slate-500");
                eligibilityExternalStatus.classList.add("text-rose-600");
            }
        }

        amountDisplayInput.addEventListener("input", syncAmountInput);
        amountDisplayInput.addEventListener("change", function () {
            syncAmountInput();
            resetFinancialPreview();
        });
        tenorInput.addEventListener("change", resetFinancialPreview);
        syncAmountInput();
        loadExternalEligibilitySummary();
        const applicantOtpUi = bindOtpLiveStatus(
            applicantSignatureOtpInput,
            applicantSignatureOtpLiveStatus,
            finalSubmitButton,
            async function (code) {
                const applicationId = "${formValues['applicationId']}";
                const response = await fetch("/app/loan-applications/verify-signature-otp", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                        "Accept": "application/json"
                    },
                    body: new URLSearchParams({
                        "${_csrf.parameterName}": csrfInput.value,
                        "applicationId": applicationId,
                        "otpCode": code
                    })
                });
                const payload = await response.json();
                if (!response.ok || payload.valid === false) {
                    throw new Error(payload.message || msgInvalidOtpCode);
                }
                return payload;
            }
        );

        financialButton.addEventListener("click", async function () {
            if (!form.reportValidity()) {
                return;
            }

            financialLoading.classList.remove("hidden");
            financialButton.disabled = true;
            financialButton.classList.add("opacity-60", "cursor-not-allowed");
            try {
                const response = await fetch("/app/loan-applications/financial-preview", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                        "Accept": "application/json"
                    },
                    body: new URLSearchParams({
                        "${_csrf.parameterName}": csrfInput.value,
                        "loanType": "${loanType}",
                        "amount": amountInput.value,
                        "tenorMonths": tenorInput.value,
                        "topUpLoanId": topUpLoanIdInput ? topUpLoanIdInput.value : ""
                    })
                });

                if (!response.ok) {
                    throw new Error(await readJsonErrorMessage(response, msgUnableLoadOfficialDetails));
                }

                const payload = await response.json();
                financialSnapshotInput.value = payload.snapshotJson || "";
                financialBody.innerHTML = "";

                Object.entries(payload.fields || {}).forEach(function (entry) {
                    const row = document.createElement("tr");
                    row.innerHTML = "<td class='px-3 py-2 font-medium text-slate-700'></td><td class='px-3 py-2'></td>";
                    row.children[0].textContent = entry[0];
                    row.children[1].textContent = entry[1];
                    financialBody.appendChild(row);
                });

                financialCard.classList.remove("hidden");
                showFinancialFeedback("success", payload.message || "Loan details loaded successfully.");
            } catch (error) {
                showFinancialFeedback("error", error.message || "Failed to load the loan details.");
            } finally {
                financialLoading.classList.add("hidden");
                financialButton.disabled = false;
                financialButton.classList.remove("opacity-60", "cursor-not-allowed");
            }
        });

        if (signatureOtpButton) {
            signatureOtpButton.addEventListener("click", async function () {
                applicantOtpUi.reset();
                setOtpButtonState(signatureOtpButton, "loading", msgSendOtp, msgSending, msgOtpSent);
                try {
                    const response = await fetch("/app/loan-applications/request-signature-otp", {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                            "Accept": "application/json"
                        },
                        body: new URLSearchParams({
                            "${_csrf.parameterName}": csrfInput.value,
                            "applicationId": "${formValues['applicationId']}"
                        })
                    });
                    const payload = await response.json();
                    if (!response.ok || payload.valid === false) {
                        throw new Error(payload.message || msgUnableSendOtp);
                    }
                    showSignatureOtpFeedback("success", payload.message || msgOtpEmailSent);
                    setOtpButtonState(signatureOtpButton, "sent", msgSendOtp, msgSending, msgOtpSent);
                    applicantOtpUi.markRequested();
                    if (applicantSignatureOtpInput) {
                        applicantSignatureOtpInput.focus();
                    }
                } catch (error) {
                    showSignatureOtpFeedback("error", error.message || msgUnableSendOtp);
                    setOtpButtonState(signatureOtpButton, "idle", msgSendOtp, msgSending, msgOtpSent);
                }
            });
        }

        form.addEventListener("submit", function (event) {
            const submitter = event.submitter || document.activeElement;
            const submitButton = submitter && submitter.form === form && submitter.type === "submit"
                ? submitter
                : form.querySelector("button[type='submit']");
            if (!submitButton || submitButton.disabled) {
                return;
            }
            if (actionInput) {
                actionInput.value = submitButton.dataset.formAction || "SAVE_DRAFT";
            }
            submitButton.disabled = true;
            submitButton.classList.add("opacity-70", "cursor-not-allowed");
        });

    })();
</script>

<c:if test="${requiredGuarantors gt 0}">
    <script>
        (function () {
            const required = Number("${requiredGuarantors}");
            const searchInput = document.getElementById("guarantorSearch");
            const searchButton = document.getElementById("guarantorSearchButton");
            const dropdown = document.getElementById("guarantorDropdown");
            const hint = document.getElementById("guarantorHint");
            const counter = document.getElementById("guarantorSelectedCount");
            const selectedContainer = document.getElementById("selectedGuarantors");
            const hiddenInputs = document.getElementById("selectedGuarantorInputs");
            const selected = new Map();
            const msgSelectedSuffix = "<spring:message code='newloan.js.selectedSuffix' />";
            const msgNoMatches = "<spring:message code='newloan.js.noMatches' />";
            const msgOnlySelectGuarantors = "<spring:message code='newloan.js.onlySelectGuarantors' />";
            const msgGuarantorSelected = "<spring:message code='newloan.js.guarantorSelected' />";
            const msgEnterMemberNumber = "<spring:message code='newloan.js.enterMemberNumber' />";
            const msgMatchingMembers = "<spring:message code='newloan.js.matchingMembers' />";
            const msgUnableSearchGuarantors = "<spring:message code='newloan.js.unableSearchGuarantors' />";
            const msgSelectGuarantorsDraft = "<spring:message code='newloan.js.selectGuarantorsDraft' />";

            function updateCounter() {
                counter.textContent = selected.size + " " + msgSelectedSuffix + " / " + required;
            }

            function renderHiddenInputs() {
                hiddenInputs.innerHTML = "";
                Array.from(selected.keys()).forEach(function (id) {
                    const input = document.createElement("input");
                    input.type = "hidden";
                    input.name = "guarantorIds";
                    input.value = id;
                    hiddenInputs.appendChild(input);
                });
            }

            function renderSelected() {
                selectedContainer.innerHTML = "";
                Array.from(selected.values()).forEach(function (item) {
                    const chip = document.createElement("button");
                    chip.type = "button";
                    chip.className = "inline-flex items-center gap-2 rounded-full border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700";
                    chip.innerHTML = "<span>" + item.memberNo + " - " + item.fullName + "</span><span class='text-slate-400'>x</span>";
                    chip.addEventListener("click", function () {
                        selected.delete(item.id);
                        renderSelected();
                    });
                    selectedContainer.appendChild(chip);
                });
                renderHiddenInputs();
                updateCounter();
            }

            Array.from(document.querySelectorAll(".selected-guarantor-chip")).forEach(function (chip) {
                selected.set(chip.dataset.id, {
                    id: chip.dataset.id,
                    memberNo: chip.dataset.memberNo,
                    fullName: chip.dataset.fullName
                });
            });
            renderSelected();

            function hideDropdown() {
                dropdown.classList.add("hidden");
                dropdown.innerHTML = "";
            }

            function showResults(items) {
                dropdown.innerHTML = "";
                if (!items.length) {
                    const empty = document.createElement("div");
                    empty.className = "px-4 py-3 text-sm text-slate-500";
                    empty.textContent = msgNoMatches;
                    dropdown.appendChild(empty);
                } else {
                    items.forEach(function (item) {
                        const row = document.createElement("button");
                        row.type = "button";
                        row.className = "block w-full border-b border-slate-100 px-4 py-3 text-left text-sm text-slate-700 hover:bg-slate-50";
                        row.textContent = item.memberNo + " - " + item.fullName;
                        row.addEventListener("click", function () {
                            if (selected.size >= required) {
                                hint.textContent = msgOnlySelectGuarantors.replace("{0}", required);
                                hideDropdown();
                                return;
                            }
                            selected.set(item.id, item);
                            renderSelected();
                            searchInput.value = "";
                            hint.textContent = msgGuarantorSelected;
                            hideDropdown();
                        });
                        dropdown.appendChild(row);
                    });
                }
                dropdown.classList.remove("hidden");
            }

            async function runSearch() {
                const term = searchInput.value.trim().toUpperCase();
                const validFourDigitsOrMore = /^\d{4,20}$/.test(term);
                const validFullMemberNo = /^[A-Z0-9]{4,20}$/.test(term) && /\d/.test(term);
                if (!validFourDigitsOrMore && !validFullMemberNo) {
                    hint.textContent = msgEnterMemberNumber;
                    hideDropdown();
                    return;
                }
                const response = await fetch("/app/guarantors/search?q=" + encodeURIComponent(term), {
                    headers: {
                        "X-Requested-With": "XMLHttpRequest"
                    }
                });
                const data = response.ok ? await response.json() : [];
                const filtered = data.filter(function (item) { return !selected.has(item.id); });
                hint.textContent = filtered.length + " " + msgMatchingMembers;
                showResults(filtered);
            }

            searchButton.addEventListener("click", function () {
                runSearch().catch(function () {
                    hint.textContent = msgUnableSearchGuarantors;
                    hideDropdown();
                });
            });

            const draftButton = document.querySelector("button[type='submit'][data-form-action='SAVE_DRAFT']");
            if (draftButton) {
                draftButton.addEventListener("click", function (event) {
                    if (selected.size !== required) {
                        event.preventDefault();
                        const warning = msgSelectGuarantorsDraft.replace("{0}", required);
                        hint.textContent = warning;
                        window.showToast?.("error", warning);
                    }
                });
            }

            document.addEventListener("click", function (event) {
                if (!dropdown.contains(event.target) && event.target !== searchInput) {
                    hideDropdown();
                }
            });
        })();
    </script>
</c:if>

<%@ include file="../fragments/footer.jspf" %>
