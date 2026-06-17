<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>
<%@ include file="../fragments/attachment-dropzone.jspf" %>
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

    .tenure-unit-toggle-group {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 0.18rem;
        min-height: 2.65rem;
        border: 1px solid #cbd5e1;
        border-radius: 0.45rem;
        background: #ffffff;
        padding: 0.18rem;
        box-shadow: inset 0 0 0 1px rgba(226, 232, 240, 0.65);
    }

    .tenure-unit-toggle {
        min-width: 0;
        border: 1px solid transparent;
        border-radius: 0.32rem;
        background: transparent;
        color: #475569;
        font-weight: 700;
        line-height: 1.2;
    }

    .tenure-unit-toggle:hover {
        background: #f8fafc;
        border-color: #e2e8f0;
    }

    .tenure-unit-toggle.is-active {
        background: #9ACBEA;
        border-color: #86bddd;
        color: #0f172a;
        box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
    }

    .tenure-unit-toggle.is-active:hover {
        background: #8DBFDE;
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="newloan.breadcrumb" /></p>
    <h1 class="erp-page-title"><spring:message code="newloan.title" />: <c:out value="${loanProductName}" /></h1>
    <p class="erp-page-subtitle"><c:out value="${loanProductDescription}" /></p>
</div>
<c:set var="declarationSaccoName" value="${not empty activeSaccoName ? activeSaccoName : 'your SACCO'}" />
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
                <li><spring:message code="newloan.applicationRules.amountRange" /> <strong>${minimumAmountLabel}</strong> <spring:message code="common.to" text="to" /> <strong>${maximumAmountLabel}</strong></li>
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
            <spring:message code="newloan.topUp.notice" text="This application is being created as a loan top-up from application" />
            <strong>${topUpSourceLoan.applicationNumber}</strong><c:if test="${not empty topUpSourceLoan.loanId}"> (<spring:message code="newloan.topUp.loanLabel" text="loan" /> ${topUpSourceLoan.loanId})</c:if>.
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
            <p class="mt-1 text-xs text-slate-500"><spring:message code="newloan.allowedRange" /> ${minimumAmountLabel} <spring:message code="common.to" text="to" /> ${maximumAmountLabel}</p>
        </div>
        <div>
            <label id="tenorDisplayLabel" for="tenorDisplayInput" class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="newloan.tenor.label" /></label>
            <input id="tenorInput" type="hidden" name="tenorMonths" value="${formValues['tenorMonths']}" />
            <div class="tenure-unit-toggle-group mb-2 text-sm" role="group" aria-label="Tenure unit">
                <button type="button" class="tenure-unit-toggle is-active px-3 py-2 transition" data-tenure-unit="MONTHS" aria-pressed="true">Months</button>
                <button type="button" class="tenure-unit-toggle px-3 py-2 transition" data-tenure-unit="YEARS" aria-pressed="false">Years</button>
            </div>
            <div>
                <input id="tenorDisplayInput" type="number"
                       min="${product.minimumRepaymentMonths}"
                       max="${product.maxRepaymentMonths}"
                       value="${formValues['tenorMonths']}"
                       placeholder="12"
                       data-min-months="${product.minimumRepaymentMonths}"
                       data-max-months="${product.maxRepaymentMonths}"
                       class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                       required />
            </div>
            <p id="tenorRangeHelp" class="mt-1 text-xs text-slate-500"
               data-prefix="<spring:message code='newloan.allowedTenureRange' />"
               data-to-label="<spring:message code='common.to' text='to' />"
               data-month-label="<spring:message code='common.months' text='month(s)' />"
               data-year-label="year(s)"></p>
        </div>
    </div>

    <div class="erp-section-muted">
        <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="newloan.purpose.label" /></label>
        <textarea name="purpose"
                  rows="4"
                  maxlength="120"
                  data-loan-purpose
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
                           value="${fieldValue}" placeholder="<spring:message code='newloan.number.placeholder' text='e.g. 100000' />"
                           <c:if test="${field.required}">required</c:if> />
                </c:when>
                <c:when test="${field.type eq 'textarea'}">
                    <textarea class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                              name="${field.name}"
                              placeholder="<spring:message code='newloan.field.placeholder' arguments='${field.name}' text='Enter {0}' />"
                              <c:if test="${field.required}">required</c:if>>${fieldValue}</textarea>
                </c:when>
                <c:otherwise>
                    <input class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                           type="text" name="${field.name}"
                           value="${fieldValue}" placeholder="<spring:message code='newloan.field.placeholder' arguments='${field.name}' text='Enter {0}' />"
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
            <span class="sr-only"><spring:message code="newloan.loanDetails.loading" /></span>
            <div class="skeleton-table" aria-hidden="true">
                <span class="skeleton skeleton-title"></span>
                <div class="skeleton-table-row">
                    <span class="skeleton skeleton-text"></span>
                    <span class="skeleton skeleton-text"></span>
                </div>
                <div class="skeleton-table-row">
                    <span class="skeleton skeleton-text"></span>
                    <span class="skeleton skeleton-text"></span>
                </div>
                <div class="skeleton-table-row">
                    <span class="skeleton skeleton-text"></span>
                    <span class="skeleton skeleton-text" style="width: 72%;"></span>
                </div>
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

    <div id="repaymentSchedulePreviewCard" class="<c:if test='${empty repaymentSchedulePreviewRows}'>hidden </c:if>erp-section-muted">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div>
                <h5 class="erp-panel-title">Repayment Scheduler</h5>
                <p class="text-sm text-slate-500">Estimated monthly installments with loan amount, interest, and balance after each payment.</p>
            </div>
        </div>
        <div class="erp-table-wrap overflow-x-auto">
            <table class="erp-table">
                <thead>
                <tr>
                    <th>Pmt No.</th>
                    <th>Month</th>
                    <th>Beginning Balance</th>
                    <th>Amount to Pay</th>
                    <th>Loan Amount</th>
                    <th>Interest</th>
                    <th>Ending Balance</th>
                </tr>
                </thead>
                <tbody id="repaymentSchedulePreviewBody">
                <c:forEach items="${repaymentSchedulePreviewRows}" var="row">
                    <tr>
                        <td class="px-3 py-2 font-medium text-slate-700">${row.pmtNo}</td>
                        <td class="px-3 py-2">${row.month}</td>
                        <td class="px-3 py-2">${row.beginningBalance}</td>
                        <td class="px-3 py-2">${row.payment}</td>
                        <td class="px-3 py-2">${row.loanAmount}</td>
                        <td class="px-3 py-2">${row.interest}</td>
                        <td class="px-3 py-2">${row.endingBalance}</td>
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

            <div class="relative flex flex-col gap-2 sm:flex-row sm:items-center ">
                <select id="guarantorSearchMode"
                        class="w-full rounded-lg border border-slate-300 bg-white px-3 py-3 text-sm focus:border-sacco-blue focus:outline-none">
                    <option value="number"><spring:message code="newloan.guarantors.modeNumber" /></option>
                    <option value="name"><spring:message code="newloan.guarantors.modeName" /></option>
                </select>
                <input id="guarantorSearch" type="text" autocomplete="off" placeholder="<spring:message code='newloan.guarantors.placeholder' />"
                       class="w-full sm:flex-1 rounded-lg border border-slate-300 bg-white px-3 py-3.5 text-sm focus:border-sacco-blue focus:outline-none" />
                <button id="guarantorSearchButton" type="button" class="shrink-0 rounded-md bg-white border-slate-300 px-5 py-3.5 text-sm font-semibold text-gray hover:bg-sacco-blue/90"><spring:message code="common.search" /></button>
                <div id="guarantorDropdown" class="absolute left-0 right-0 top-full z-20 mt-2 hidden max-h-52 overflow-y-auto rounded border border-slate-200 bg-white shadow-lg"></div>
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
            <c:if test="${not empty guarantorValidationErrorId}">
                <div id="guarantorValidationErrorMarker"
                     hidden
                     data-guarantor-id="${fn:escapeXml(guarantorValidationErrorId)}"
                     data-message="${fn:escapeXml(guarantorValidationErrorMessage)}"></div>
            </c:if>
        </div>
    </c:if>

    <div class="erp-section-muted">
        <div class="mb-2 flex flex-wrap items-start justify-between gap-2">
            <div>
                <h5 class="erp-panel-title"><spring:message code="newloan.attachments.title" /></h5>
                <p class="text-sm text-slate-500"><spring:message code="newloan.attachments.helper" /></p>
            </div>
            <span class="rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-wide ${product.applicantAttachmentRequired ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-600'}">
                <c:choose>
                    <c:when test="${product.applicantAttachmentRequired}">
                        <spring:message code="common.required" text="Required" />
                    </c:when>
                    <c:otherwise>
                        <spring:message code="common.optional" text="Optional" />
                    </c:otherwise>
                </c:choose>
            </span>
        </div>
        <label class="attachment-dropzone" data-attachment-dropzone>
            <input type="file" name="attachments" multiple class="attachment-dropzone-input" data-attachment-input />
            <span class="attachment-dropzone-main">
                <span class="attachment-dropzone-copy">
                    <span class="attachment-dropzone-title">Drop files here or choose from device</span>
                    <span class="attachment-dropzone-help">Images and documents can be attached.</span>
                    <span class="attachment-dropzone-files" data-attachment-files>No file selected</span>
                </span>
                <span class="attachment-dropzone-action">Choose files</span>
            </span>
        </label>
        <p class="mt-2 text-sm text-slate-500"><spring:message code="newloan.attachments.note" /></p>
    </div>

    <c:if test="${requiredGuarantors le 0}">
    <div class="erp-section-muted">
        <h5 class="erp-panel-title"><spring:message code="newloan.declaration.title" /></h5>
        <div class="mt-3 space-y-3 rounded-lg border border-slate-200 bg-white px-4 py-4 text-sm leading-7 text-slate-700">
            <p><spring:message code="newloan.declaration.text" />
                <strong>
                    <c:choose>
                        <c:when test="${not empty currentMember and not empty currentMember.fullName}">${currentMember.fullName}</c:when>
                        <c:otherwise><spring:message code="newloan.declaration.applicantFallback" /></c:otherwise>
                    </c:choose>
                </strong><spring:message code="newloan.declaration.textTail" arguments="${declarationSaccoName}" />
            </p>
            <label class="flex items-start gap-3 rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-700">
                <input id="loanTermsAccepted" type="checkbox" name="termsAccepted" value="true" class="mt-1 h-4 w-4 rounded border-slate-300 text-sacco-blue focus:ring-sacco-blue" />
                <span>I accept the terms and conditions for this loan application.</span>
            </label>
            <div class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4">
                <c:choose>
                    <c:when test="${empty formValues['applicationId']}">
                        <div>
                            <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="newloan.otp.title" /></div>
                            <p class="mt-2 text-sm text-slate-600"><spring:message code="newloan.otp.draftFirst" /></p>
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
                                   data-otp-hidden="true" data-otp-label="Applicant OTP code"
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
    </c:if>

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
        const tenorDisplayInput = document.getElementById("tenorDisplayInput");
        const tenorDisplayLabel = document.getElementById("tenorDisplayLabel");
        const tenorRangeHelp = document.getElementById("tenorRangeHelp");
        const tenureUnitButtons = Array.from(document.querySelectorAll("[data-tenure-unit]"));
        const actionInput = document.getElementById("loanFormAction");
        const financialButton = document.getElementById("loadFinancialDetailsButton");
        const financialFeedback = document.getElementById("financialFeedback");
        const financialLoading = document.getElementById("financialLoading");
        const financialCard = document.getElementById("financialPreviewCard");
        const financialBody = document.getElementById("financialPreviewBody");
        const financialSnapshotInput = document.getElementById("financialSnapshotJson");
        const repaymentScheduleCard = document.getElementById("repaymentSchedulePreviewCard");
        const repaymentScheduleBody = document.getElementById("repaymentSchedulePreviewBody");
        const topUpLoanIdInput = document.getElementById("topUpLoanId");
        const signatureOtpButton = document.getElementById("requestApplicantSignatureOtpButton");
        const signatureOtpFeedback = document.getElementById("applicantSignatureOtpFeedback");
        const applicantSignatureOtpInput = document.getElementById("applicantSignatureOtpCode");
        const applicantSignatureOtpLiveStatus = document.getElementById("applicantSignatureOtpLiveStatus");
        const termsAcceptedInput = document.getElementById("loanTermsAccepted");
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

        function clearSubmitConfirmation() {
            delete form.dataset.confirmEyebrow;
            delete form.dataset.confirmTitle;
            delete form.dataset.confirmMessage;
            delete form.dataset.confirmProceed;
        }

        function setSubmitConfirmation(submitButton) {
            const proceedLabel = (submitButton?.textContent || "").trim() || "Submit Application";
            form.dataset.confirmEyebrow = "Confirm Submission";
            form.dataset.confirmTitle = proceedLabel;
            form.dataset.confirmMessage = "Continue with this loan application submission? It will leave draft editing and move to the next required approval step.";
            form.dataset.confirmProceed = proceedLabel;
        }

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

        let tenureUnit = "MONTHS";

        function minTenorMonths() {
            return Number(tenorDisplayInput?.dataset.minMonths || 1);
        }

        function maxTenorMonths() {
            return Number(tenorDisplayInput?.dataset.maxMonths || 0);
        }

        function yearsFromMonths(months, rounder) {
            if (!Number.isFinite(months) || months <= 0) {
                return 0;
            }
            return rounder(months / 12);
        }

        function visibleTenorBounds() {
            const minMonths = minTenorMonths();
            const maxMonths = maxTenorMonths();
            if (tenureUnit === "YEARS") {
                return {
                    min: Math.max(1, yearsFromMonths(minMonths, Math.ceil)),
                    max: maxMonths > 0 ? yearsFromMonths(maxMonths, Math.floor) : 0
                };
            }
            return {
                min: minMonths,
                max: maxMonths
            };
        }

        function hasWholeYearTenureOption() {
            const maxMonths = maxTenorMonths();
            return tenureUnit !== "YEARS" || maxMonths <= 0 || yearsFromMonths(maxMonths, Math.floor) >= Math.max(1, yearsFromMonths(minTenorMonths(), Math.ceil));
        }

        function renderTenureCopy() {
            if (tenorDisplayLabel) {
                tenorDisplayLabel.textContent = tenureUnit === "YEARS" ? "Total Years to Repay" : "Total Months to Repay";
            }
            if (tenorRangeHelp) {
                if (!hasWholeYearTenureOption()) {
                    tenorRangeHelp.textContent = "Allowed range: no full-year tenure for this product. Choose Months.";
                    return;
                }
                const bounds = visibleTenorBounds();
                const prefix = tenorRangeHelp.dataset.prefix || "Allowed range:";
                const toLabel = tenorRangeHelp.dataset.toLabel || "to";
                const unitLabel = tenureUnit === "YEARS"
                    ? (tenorRangeHelp.dataset.yearLabel || "year(s)")
                    : (tenorRangeHelp.dataset.monthLabel || "month(s)");
                tenorRangeHelp.textContent = maxTenorMonths() > 0
                    ? prefix + " " + bounds.min + " " + toLabel + " " + bounds.max + " " + unitLabel
                    : prefix + " " + bounds.min + "+ " + unitLabel;
            }
        }

        function monthsFromVisibleTenor() {
            const rawValue = Number(tenorDisplayInput ? tenorDisplayInput.value : tenorInput.value);
            if (!Number.isFinite(rawValue) || rawValue <= 0) {
                return "";
            }
            return tenureUnit === "YEARS" ? Math.round(rawValue) * 12 : Math.round(rawValue);
        }

        function refreshTenureConstraints() {
            if (!tenorDisplayInput) {
                return;
            }
            if (tenureUnit === "YEARS") {
                const bounds = visibleTenorBounds();
                tenorDisplayInput.min = bounds.min;
                if (bounds.max >= bounds.min) {
                    tenorDisplayInput.max = bounds.max;
                } else {
                    tenorDisplayInput.removeAttribute("max");
                }
                tenorDisplayInput.step = "1";
                tenorDisplayInput.placeholder = "1";
            } else {
                tenorDisplayInput.min = minTenorMonths();
                const maxMonths = maxTenorMonths();
                if (maxMonths > 0) {
                    tenorDisplayInput.max = maxMonths;
                } else {
                    tenorDisplayInput.removeAttribute("max");
                }
                tenorDisplayInput.step = "1";
                tenorDisplayInput.placeholder = "12";
            }
            renderTenureCopy();
        }

        function syncTenorInput() {
            if (!tenorDisplayInput || !tenorInput) {
                return true;
            }
            const months = monthsFromVisibleTenor();
            tenorInput.value = months;
            tenorDisplayInput.setCustomValidity("");
            if (!months) {
                return false;
            }
            if (tenureUnit === "YEARS" && !/^\d+$/.test(String(tenorDisplayInput.value || "").trim())) {
                tenorDisplayInput.setCustomValidity("Enter whole years only.");
                return false;
            }
            const minMonths = minTenorMonths();
            const maxMonths = maxTenorMonths();
            if (!hasWholeYearTenureOption()) {
                tenorDisplayInput.setCustomValidity("This product does not allow a whole-year tenure. Choose Months.");
                return false;
            }
            if (months < minMonths || (maxMonths > 0 && months > maxMonths)) {
                const bounds = visibleTenorBounds();
                const unitLabel = tenureUnit === "YEARS" ? "years" : "months";
                const rangeLabel = maxMonths > 0 ? "between " + bounds.min + " and " + bounds.max : "of at least " + bounds.min;
                tenorDisplayInput.setCustomValidity("Choose a tenure " + rangeLabel + " " + unitLabel + ".");
                return false;
            }
            return true;
        }

        function setTenureUnit(unit) {
            const currentMonths = Number(tenorInput.value || monthsFromVisibleTenor());
            tenureUnit = unit === "YEARS" ? "YEARS" : "MONTHS";
            tenureUnitButtons.forEach(function (button) {
                const active = button.dataset.tenureUnit === tenureUnit;
                button.setAttribute("aria-pressed", active ? "true" : "false");
                button.classList.toggle("is-active", active);
            });
            refreshTenureConstraints();
            if (Number.isFinite(currentMonths) && currentMonths > 0) {
                tenorDisplayInput.value = tenureUnit === "YEARS"
                    ? String(Math.max(1, Math.round(currentMonths / 12)))
                    : String(Math.round(currentMonths));
            }
            renderTenureCopy();
            syncTenorInput();
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
            renderRepaymentSchedule([]);
            document.dispatchEvent(new CustomEvent("loanFinancialSnapshotChanged"));
            showFinancialFeedback("error", msgLoanAmountChanged);
        }

        function renderRepaymentSchedule(rows) {
            if (!repaymentScheduleCard || !repaymentScheduleBody) {
                return;
            }
            repaymentScheduleBody.innerHTML = "";
            (rows || []).forEach(function (item) {
                const row = document.createElement("tr");
                row.innerHTML = ""
                    + "<td class='px-3 py-2 font-medium text-slate-700'></td>"
                    + "<td class='px-3 py-2'></td>"
                    + "<td class='px-3 py-2'></td>"
                    + "<td class='px-3 py-2'></td>"
                    + "<td class='px-3 py-2'></td>"
                    + "<td class='px-3 py-2'></td>"
                    + "<td class='px-3 py-2'></td>";
                row.children[0].textContent = item.pmtNo || "-";
                row.children[1].textContent = item.month || "-";
                row.children[2].textContent = item.beginningBalance || "-";
                row.children[3].textContent = item.payment || item.installment || "-";
                row.children[4].textContent = item.loanAmount || item.principal || "-";
                row.children[5].textContent = item.interest || "-";
                row.children[6].textContent = item.endingBalance || item.outstandingBalance || "-";
                repaymentScheduleBody.appendChild(row);
            });
            repaymentScheduleCard.classList.toggle("hidden", repaymentScheduleBody.children.length === 0);
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
                    window.SaccosOtp?.focusBoxes(otpInput);
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
        tenorDisplayInput?.addEventListener("input", syncTenorInput);
        tenorDisplayInput?.addEventListener("change", function () {
            syncTenorInput();
            resetFinancialPreview();
        });
        tenureUnitButtons.forEach(function (button) {
            button.addEventListener("click", function () {
                setTenureUnit(button.dataset.tenureUnit);
                resetFinancialPreview();
                tenorDisplayInput?.focus();
            });
        });
        syncAmountInput();
        setTenureUnit("MONTHS");
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
            syncTenorInput();
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
                        "applicationId": "${formValues['applicationId']}",
                        "topUpLoanId": topUpLoanIdInput ? topUpLoanIdInput.value : ""
                    })
                });

                if (!response.ok) {
                    throw new Error(await readJsonErrorMessage(response, msgUnableLoadOfficialDetails));
                }

                const payload = await response.json();
                financialSnapshotInput.value = payload.snapshotJson || "";
                document.dispatchEvent(new CustomEvent("loanFinancialSnapshotChanged", { detail: payload }));
                financialBody.innerHTML = "";

                Object.entries(payload.fields || {}).forEach(function (entry) {
                    const row = document.createElement("tr");
                    row.innerHTML = "<td class='px-3 py-2 font-medium text-slate-700'></td><td class='px-3 py-2'></td>";
                    row.children[0].textContent = entry[0].replace(/\bPrincipal\b/g, "Loan Amount");
                    row.children[1].textContent = entry[1];
                    financialBody.appendChild(row);
                });

                financialCard.classList.remove("hidden");
                renderRepaymentSchedule(payload.repaymentSchedule || []);
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
                    window.SaccosOtp?.startCooldown(signatureOtpButton, payload, { idle: msgSendOtp });
                    applicantOtpUi.markRequested();
                    if (applicantSignatureOtpInput) {
                        window.SaccosOtp?.focusBoxes(applicantSignatureOtpInput);
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
            syncTenorInput();
            if (!tenorDisplayInput.reportValidity()) {
                event.preventDefault();
                return;
            }
            if (actionInput && actionInput.value === "SEND_TO_GUARANTORS") {
                if (termsAcceptedInput && !termsAcceptedInput.checked) {
                    event.preventDefault();
                    termsAcceptedInput.required = true;
                    termsAcceptedInput.reportValidity();
                    return;
                }
                setSubmitConfirmation(submitButton);
            } else {
                if (termsAcceptedInput) {
                    termsAcceptedInput.required = false;
                }
                clearSubmitConfirmation();
            }
            if (form.dataset.confirmMessage && form.dataset.confirmBypass !== "true") {
                return;
            }
            submitButton.disabled = true;
            submitButton.classList.add("opacity-70", "cursor-not-allowed");
        });

    })();
</script>

<script>
    (function () {
        const purpose = document.querySelector("[data-loan-purpose]");
        if (!purpose) {
            return;
        }
        purpose.addEventListener("input", function () {
            const start = purpose.selectionStart;
            const end = purpose.selectionEnd;
            purpose.value = purpose.value.toUpperCase();
            purpose.setSelectionRange(start, end);
            const words = purpose.value.trim().split(/\s+/).filter(Boolean);
            if (words.length > 10) {
                purpose.setCustomValidity("Use 10 words or fewer.");
            } else {
                purpose.setCustomValidity("");
            }
        });
    })();
</script>

<c:if test="${requiredGuarantors gt 0}">
    <script>
        (function () {
            const form = document.getElementById("loanApplicationForm");
            const required = Number("${requiredGuarantors}");
            const searchMode = document.getElementById("guarantorSearchMode");
            const searchInput = document.getElementById("guarantorSearch");
            const searchButton = document.getElementById("guarantorSearchButton");
            const dropdown = document.getElementById("guarantorDropdown");
            const hint = document.getElementById("guarantorHint");
            const counter = document.getElementById("guarantorSelectedCount");
            const selectedContainer = document.getElementById("selectedGuarantors");
            const hiddenInputs = document.getElementById("selectedGuarantorInputs");
            const validationMarker = document.getElementById("guarantorValidationErrorMarker");
            const serverGuarantorErrorId = validationMarker ? validationMarker.dataset.guarantorId : "";
            const serverGuarantorErrorMessage = validationMarker ? validationMarker.dataset.message : "";
            let serverGuarantorErrorRevealed = false;
            const selected = new Map();
            const msgSelectedSuffix = "<spring:message code='newloan.js.selectedSuffix' />";
            const msgNoMatches = "<spring:message code='newloan.js.noMatches' />";
            const msgOnlySelectGuarantors = "<spring:message code='newloan.js.onlySelectGuarantors' />";
            const msgGuarantorSelected = "<spring:message code='newloan.js.guarantorSelected' />";
            const msgEnterMemberNumber = "<spring:message code='newloan.js.enterMemberNumber' />";
            const msgEnterGuarantorName = "<spring:message code='newloan.js.enterGuarantorName' />";
            const msgMatchingMembers = "<spring:message code='newloan.js.matchingMembers' />";
            const msgUnableSearchGuarantors = "<spring:message code='newloan.js.unableSearchGuarantors' />";
            const msgSelectGuarantorsDraft = "<spring:message code='newloan.js.selectGuarantorsDraft' />";
            const numberPlaceholder = "<spring:message code='newloan.guarantors.placeholder' />";
            const namePlaceholder = "<spring:message code='newloan.guarantors.namePlaceholder' />";
            const numberHint = "<spring:message code='newloan.guarantors.numberHint' />";
            const nameHint = "<spring:message code='newloan.guarantors.nameHint' />";

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
                    const hasServerError = serverGuarantorErrorId && item.id === serverGuarantorErrorId;
                    chip.className = "inline-flex items-center gap-2 rounded-full border px-3 py-2 text-sm font-medium "
                        + (hasServerError ? "border-rose-300 bg-rose-50 text-rose-800 ring-2 ring-rose-100" : "border-slate-300 bg-white text-slate-700");
                    chip.dataset.guarantorId = item.id;
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

            function revealServerGuarantorError() {
                if (serverGuarantorErrorRevealed || !serverGuarantorErrorId) {
                    return;
                }
                const row = Array.from(selectedContainer.querySelectorAll("[data-guarantor-id]")).find(function (candidate) {
                    return candidate.dataset.guarantorId === serverGuarantorErrorId;
                });
                if (!row) {
                    return;
                }
                serverGuarantorErrorRevealed = true;
                row.scrollIntoView({ behavior: "smooth", block: "center" });
                if (serverGuarantorErrorMessage) {
                    hint.textContent = serverGuarantorErrorMessage;
                }
            }

            Array.from(document.querySelectorAll(".selected-guarantor-chip")).forEach(function (chip) {
                selected.set(chip.dataset.id, {
                    id: chip.dataset.id,
                    memberNo: chip.dataset.memberNo,
                    fullName: chip.dataset.fullName
                });
            });
            renderSelected();
            window.setTimeout(revealServerGuarantorError, 120);

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
                        const eligible = item.eligible !== "false";
                        row.className = "block w-full border-b border-slate-100 px-4 py-3 text-left text-sm " + (eligible ? "text-slate-700 hover:bg-slate-50" : "cursor-not-allowed bg-slate-50 text-slate-400");
                        row.innerHTML = "<span class='block font-semibold'>" + item.memberNo + " - " + item.fullName + "</span>"
                            + (!eligible && item.disabledReason ? "<span class='mt-1 block text-xs text-rose-600'>" + item.disabledReason + "</span>" : "");
                        row.addEventListener("click", function () {
                            if (!eligible) {
                                hint.textContent = item.disabledReason || "This guarantor is disabled by SACCO policy.";
                                return;
                            }
                            if (selected.size >= required) {
                                hint.textContent = msgOnlySelectGuarantors.replace("{0}", required);
                                hideDropdown();
                                return;
                            }
                            selected.set(item.id, {
                                id: item.id,
                                memberNo: item.memberNo,
                                fullName: item.fullName
                            });
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
                const mode = searchMode.value === "name" ? "name" : "number";
                let term = searchInput.value.trim();
                if (mode === "name") {
                    term = term.toLowerCase();
                    if (term.length < 2) {
                        hint.textContent = msgEnterGuarantorName;
                        hideDropdown();
                        return;
                    }
                } else {
                    term = term.toUpperCase();
                    const validFourDigitsOrMore = /^\d{4,20}$/.test(term);
                    const validFullMemberNo = /^[A-Z0-9]{4,20}$/.test(term) && /\d/.test(term);
                    if (!validFourDigitsOrMore && !validFullMemberNo) {
                        hint.textContent = msgEnterMemberNumber;
                        hideDropdown();
                        return;
                    }
                }
                const query = new URLSearchParams({ q: term, searchBy: mode, loanType: "${loanType}" });
                const response = await fetch("/app/guarantors/search?" + query.toString(), {
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

            searchMode.addEventListener("change", function () {
                const mode = searchMode.value === "name" ? "name" : "number";
                searchInput.value = "";
                searchInput.placeholder = mode === "name" ? namePlaceholder : numberPlaceholder;
                hint.textContent = mode === "name" ? nameHint : numberHint;
                hideDropdown();
                searchInput.focus();
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

            form.addEventListener("submit", function (event) {
                const submitter = event.submitter || document.activeElement;
                const action = submitter && submitter.dataset ? submitter.dataset.formAction : "";
                if (action !== "SAVE_DRAFT" && action !== "SEND_TO_GUARANTORS") {
                    return;
                }
                if (selected.size !== required) {
                    const warning = msgSelectGuarantorsDraft.replace("{0}", required);
                    event.preventDefault();
                    hint.textContent = warning;
                    window.showToast?.("error", warning);
                    return;
                }
            }, true);

            document.addEventListener("click", function (event) {
                if (!dropdown.contains(event.target) && event.target !== searchInput) {
                    hideDropdown();
                }
            });
        })();
    </script>
</c:if>

<%@ include file="../fragments/confirm-modal.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
