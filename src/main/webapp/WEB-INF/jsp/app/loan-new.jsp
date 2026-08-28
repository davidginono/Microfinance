<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>
<%@ include file="../fragments/attachment-dropzone.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="newloan.breadcrumb" /></p>
    <h1 class="erp-page-title"><spring:message code="newloan.title" />: <c:out value="${loanProductName}" /></h1>
</div>
<c:set var="declarationSaccoName" value="${not empty activeSaccoName ? activeSaccoName : 'your SACCO'}" />
<div class="mb-4 erp-section-muted">
    <div class="flex flex-wrap items-center gap-2">
        <h5 class="erp-panel-title"><spring:message code="newloan.eligibilityGuide.title" /></h5>
        <span id="eligibilityExternalInlineStatus" class="inline-flex items-center gap-2 text-xs font-medium text-slate-500">
            <span id="eligibilityExternalSpinner" class="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-slate-300 border-t-sacco-blue"></span>
            <span id="eligibilityExternalStatusText"><spring:message code="newloan.applicationRules.loading" /></span>
        </span>
    </div>
    <ul class="mt-3 list-disc space-y-1 pl-5 text-sm text-slate-700">
        <li><spring:message code="newloan.applicationRules.savings" /> <strong id="eligibilitySavingsValue">${savingsLabel}</strong></li>
        <c:if test="${savingsLimitCheckRequired}">
            <li><spring:message code="newloan.applicationRules.maximumAllowed" /> <strong id="eligibilityMaxAllowedValue">${maxAllowedLabel}</strong></li>
        </c:if>
        <li><spring:message code="newloan.applicationRules.amountRange" /> <strong>${minimumAmountLabel}</strong> <spring:message code="common.to" text="to" /> <strong>${maximumAmountLabel}</strong></li>
    </ul>
</div>

<form id="loanApplicationForm" method="post" action="/app/loan-applications" enctype="multipart/form-data" class="erp-form-wrap loan-application-create-page space-y-5">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    <input type="hidden" name="loanProductId" value="${loanProductId}" />
    <input type="hidden" name="loanType" value="${loanType}" />
    <input type="hidden" id="loanFormAction" name="action" value="SAVE_DRAFT" />
    <input type="hidden" id="financialSnapshotJson" name="financialSnapshotJson" value="${fn:escapeXml(formValues['financialSnapshotJson'])}" />
    <input type="hidden" id="topUpLoanId" name="topUpLoanId" value="${topUpLoanId}" />
    <c:if test="${not empty formValues['applicationId']}">
        <input type="hidden" name="applicationId" value="${formValues['applicationId']}" />
    </c:if>

    <nav class="loan-application-steps" aria-label="Loan application steps">
        <button type="button" class="is-current" data-loan-step-button="1" aria-current="step"><span>1</span><span>Request</span></button>
        <button type="button" data-loan-step-button="2"><span>2</span><span>Loan Calculations</span></button>
        <button type="button" data-loan-step-button="3"><span>3</span><span>Select Guarantor</span></button>
        <button type="button" data-loan-step-button="4"><span>4</span><span>Attachments</span></button>
        <button type="button" data-loan-step-button="5"><span>5</span><span>Save and Submit</span></button>
    </nav>

    <section class="loan-flow-panel" data-loan-flow-step="1" aria-labelledby="loanFlowStep1Title">
        <div class="loan-flow-panel-heading">
            <span class="loan-flow-step-number">1</span>
            <div>
                <h2 id="loanFlowStep1Title">Set your loan request</h2>
                <p>Confirm the amount, repayment period, purpose, and product-specific details.</p>
            </div>
        </div>
        <div class="loan-flow-panel-body">

    <c:if test="${not empty topUpSourceLoan}">
        <div class="border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            <spring:message code="newloan.topUp.notice" text="This application is being created as a loan top-up from application" />
            <strong>${topUpSourceLoan.applicationNumber}</strong><c:if test="${not empty topUpSourceLoan.loanId}"> (<spring:message code="newloan.topUp.loanLabel" text="loan" /> ${topUpSourceLoan.loanId})</c:if>.
        </div>
    </c:if>

    <div class="grid gap-4 md:grid-cols-2">
        <div>
            <label class="mb-1 block text-sm font-medium text-slate-700">
                <c:choose>
                    <c:when test="${topUpMode}"><spring:message code="newloan.topUp.additionalAmount" text="Additional Top-Up Amount" /></c:when>
                    <c:otherwise><spring:message code="loan.amount" /></c:otherwise>
                </c:choose>
                (TSh)
            </label>
            <input id="loanAmountInput" type="hidden" name="amount" value="${formValues['amount']}" />
            <input id="loanAmountDisplay" type="text" inputmode="decimal" autocomplete="off"
                   value="${formValues['amount']}"
                   placeholder="100,000"
                   data-min-amount="${product.minimumAmount}"
                   data-max-amount="${product.maximumAmount}"
                   class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                   required />
            <p class="mt-1 text-xs text-slate-500">
                <c:choose>
                    <c:when test="${topUpMode}">
                        <spring:message code="newloan.topUp.rangeHelp" text="The new loan principal must remain within" /> ${minimumAmountLabel} <spring:message code="common.to" text="to" /> ${maximumAmountLabel}
                    </c:when>
                    <c:otherwise>
                        <spring:message code="newloan.allowedRange" /> ${minimumAmountLabel} <spring:message code="common.to" text="to" /> ${maximumAmountLabel}
                    </c:otherwise>
                </c:choose>
            </p>
        </div>
        <div>
            <label id="tenorDisplayLabel" for="tenorDisplayInput" class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="newloan.tenor.label" /></label>
            <input id="tenorInput" type="hidden" name="tenorMonths" value="${formValues['tenorMonths']}" />
            <div class="tenure-unit-toggle-group mb-2 text-sm" role="group" aria-label="<spring:message code='newloan.tenureUnit' text='Tenure unit' />">
                <button type="button" class="tenure-unit-toggle is-active px-3 py-2 transition" data-tenure-unit="MONTHS" aria-pressed="true"><spring:message code="common.months.label" text="Months" /></button>
                <button type="button" class="tenure-unit-toggle px-3 py-2 transition" data-tenure-unit="YEARS" aria-pressed="false"><spring:message code="common.years.label" text="Years" /></button>
            </div>
            <div>
                <input id="tenorDisplayInput" type="number"
                       min="${product.minimumRepaymentMonths}"
                       max="${product.maxRepaymentMonths}"
                       value="${formValues['tenorMonths']}"
                       placeholder="12"
                       data-min-months="${product.minimumRepaymentMonths}"
                       data-max-months="${product.maxRepaymentMonths}"
                       class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                       required />
            </div>
            <p id="tenorRangeHelp" class="mt-1 text-xs text-slate-500"
               data-prefix="<spring:message code='newloan.allowedTenureRange' />"
               data-to-label="<spring:message code='common.to' text='to' />"
               data-month-label="<spring:message code='common.months' text='month(s)' />"
               data-year-label="<spring:message code='common.years' text='year(s)' />"></p>
        </div>
    </div>

    <div class="erp-section-muted">
        <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="newloan.purpose.label" /></label>
        <textarea name="purpose"
                  rows="4"
                  maxlength="120"
                  data-loan-purpose
                  class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                  placeholder="<spring:message code='newloan.purpose.placeholder' />">${formValues['purpose']}</textarea>
        <p class="mt-2 text-xs text-slate-500"><spring:message code="newloan.purpose.help" /></p>
    </div>

    <c:forEach items="${formModel.fields}" var="field">
        <c:set var="fieldValue" value="${formValues[field.name]}" />
        <div>
            <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="${field.labelKey}" text="${field.name}" /></label>
            <c:choose>
                <c:when test="${field.type eq 'select'}">
                    <select class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                            name="${field.name}"
                            <c:if test="${field.required}">required</c:if>>
                        <c:forEach items="${field.enumOptions}" var="opt">
                            <option value="${opt}" <c:if test="${fieldValue eq opt}">selected</c:if>>${opt}</option>
                        </c:forEach>
                    </select>
                </c:when>
                <c:when test="${field.type eq 'number'}">
                    <input class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                           type="number" name="${field.name}" step="0.01"
                           value="${fieldValue}" placeholder="<spring:message code='newloan.number.placeholder' text='e.g. 100000' />"
                           <c:if test="${field.required}">required</c:if> />
                </c:when>
                <c:when test="${field.type eq 'textarea'}">
                    <textarea class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                              name="${field.name}"
                              placeholder="<spring:message code='newloan.field.placeholder' arguments='${field.name}' text='Enter {0}' />"
                              <c:if test="${field.required}">required</c:if>>${fieldValue}</textarea>
                </c:when>
                <c:otherwise>
                    <input class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                           type="text" name="${field.name}"
                           value="${fieldValue}" placeholder="<spring:message code='newloan.field.placeholder' arguments='${field.name}' text='Enter {0}' />"
                           <c:if test="${field.required}">required</c:if> />
                </c:otherwise>
            </c:choose>
        </div>
    </c:forEach>

        </div>
        <div class="loan-flow-navigation">
            <button type="button" class="app-btn btn-primary" data-loan-step-next>Continue to Loan Calculations</button>
        </div>
    </section>

    <section class="loan-flow-panel" data-loan-flow-step="2" aria-labelledby="loanFlowStep2Title" hidden>
        <div class="loan-flow-panel-heading">
            <span class="loan-flow-step-number">2</span>
            <div>
                <h2 id="loanFlowStep2Title">Loan Calculations</h2>
                <p>Review the repayment estimate, fees, and schedule for this application.</p>
            </div>
        </div>
        <div class="loan-flow-panel-body">
    <div class="loan-workflow-section loan-calculations-section">
        <div class="loan-workflow-toolbar">
            <button id="loadFinancialDetailsButton" type="button" class="app-btn btn-primary">
                <spring:message code="newloan.loanDetails.button" />
            </button>
        </div>
        <div id="financialFeedback" data-auto-scroll-message="true" class="mt-3 hidden border px-4 py-3 text-sm"></div>
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
                    <span class="skeleton skeleton-text aws-skeleton-wide"></span>
                </div>
            </div>
        </div>
        <div id="financialPreviewCard" class="<c:if test='${empty financialSnapshotSections}'>hidden </c:if>mt-4 grid gap-3 md:grid-cols-2">
            <c:forEach items="${financialSnapshotSections}" var="section">
<div class="erp-table-wrap loan-summary-table" aria-label="${fn:escapeXml(section.key)}" data-aws-table-region data-aws-no-titlebar="true" data-aws-no-refresh="true" data-loading-label="Loading results...">
                    <div class="border-b border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-xs font-bold uppercase tracking-[0.16em] text-slate-600">${section.key}</p>
                    </div>
                    <div class="erp-table-scroll erp-table-scroll-sm">
                    <table class="erp-table">
                        <thead>
                        <tr>
                            <th><spring:message code="newloan.table.section" /></th>
                            <th><spring:message code="newloan.table.value" /></th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${section.value}" var="entry">
                            <tr>
                                <td class="px-3 py-2 font-medium text-slate-700">${entry.key}</td>
                                <td class="px-3 py-2">${entry.value}</td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
        </div>
            </c:forEach>
    </div>
            </div>

    <div id="repaymentSchedulePreviewCard" class="<c:if test='${empty repaymentSchedulePreviewRows}'>hidden </c:if>erp-section-muted">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
            <div>
                <h5 class="erp-panel-title"><spring:message code="repayment.scheduler" text="Repayment Scheduler" /></h5>
        </div>
        </div>
<div class="erp-table-wrap" aria-label="<spring:message code='repayment.scheduler' text='Repayment Scheduler' />" data-aws-table-region data-aws-no-titlebar="true" data-aws-no-refresh="true" data-loading-label="Loading results...">
            <div class="erp-table-scroll erp-table-scroll-sm">
            <table class="erp-table">
                <thead>
                <tr>
                    <th><spring:message code="repayment.pmtNo" text="Pmt No." /></th>
                    <th><spring:message code="repayment.month" text="Month" /></th>
                    <th><spring:message code="repayment.beginningBalance" text="Beginning Balance" /></th>
                    <th><spring:message code="repayment.amountToPay" text="Amount to Pay" /></th>
                    <th><spring:message code="loan.amount.label" text="Loan Amount" /></th>
                    <th><spring:message code="repayment.interest" text="Interest" /></th>
                    <th><spring:message code="repayment.endingBalance" text="Ending Balance" /></th>
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
        </div>

            </div>
        <div class="loan-flow-navigation">
            <button type="button" class="app-btn btn-neutral" data-loan-step-back>Back</button>
            <button type="button" class="app-btn btn-primary" data-loan-step-next>Continue to Select Guarantor</button>
        </div>
    </section>

    <section class="loan-flow-panel" data-loan-flow-step="3" aria-labelledby="loanFlowStep3Title" hidden>
        <div class="loan-flow-panel-heading">
            <span class="loan-flow-step-number">3</span>
            <div>
                <h2 id="loanFlowStep3Title">Select Guarantor</h2>
                <p>Choose the required guarantor and approval method for this application.</p>
                </div>
            </div>
        <div class="loan-flow-panel-body">
    <c:if test="${requiredGuarantors gt 0}">
        <div class="loan-workflow-section loan-guarantor-section">
            <div class="loan-selection-header">
                <div>
                    <h5 class="erp-panel-title">
                        <c:choose>
                            <c:when test="${requiredGuarantors eq 1}"><spring:message code="newloan.guarantors.titleSingular" arguments="${requiredGuarantors}" text="Select {0} Guarantor" /></c:when>
                            <c:otherwise><spring:message code="newloan.guarantors.titlePlural" arguments="${requiredGuarantors}" text="Select {0} Guarantors" /></c:otherwise>
                        </c:choose>
                    </h5>
                </div>
                <span id="guarantorSelectedCount" class="app-badge loan-selection-counter">
                    <spring:message code="newloan.guarantors.counter" arguments="${requiredGuarantors}" />
                </span>
                </div>

            <div class="loan-guarantor-mode">
                <div class="loan-control-label">
                    <spring:message code="newloan.guarantors.approvalMode" text="Guarantor approval mode" />
            </div>
                <input type="hidden"
                       id="guarantorApprovalMode"
                       name="guarantorApprovalMode"
                       value="${guarantorApprovalMode eq 'DIRECT_OTP' ? 'DIRECT_OTP' : 'LOGIN'}" />
                <div class="loan-segmented-control"
                     role="group"
                     aria-label="Guarantor approval mode">
                    <button type="button"
                            class="guarantor-approval-mode-option loan-segmented-control__button ${guarantorApprovalMode ne 'DIRECT_OTP' ? 'is-active' : ''}"
                            data-guarantor-approval-mode-option="LOGIN"
                            aria-pressed="${guarantorApprovalMode ne 'DIRECT_OTP'}">
                        <spring:message code="newloan.guarantors.loginApproval" text="Login approval" />
                    </button>
                    <button type="button"
                            class="guarantor-approval-mode-option loan-segmented-control__button ${guarantorApprovalMode eq 'DIRECT_OTP' ? 'is-active' : ''}"
                            data-guarantor-approval-mode-option="DIRECT_OTP"
                            aria-pressed="${guarantorApprovalMode eq 'DIRECT_OTP'}">
                        <spring:message code="newloan.guarantors.directOtp" text="Direct OTP" />
                    </button>
            </div>
            </div>

            <div class="loan-guarantor-search-row">
                    <select id="guarantorSearchMode"
                        data-native-select="true"
                        class="fcms-control loan-guarantor-search-mode"
                        aria-label="Guarantor search field">
                    <option value="number"><spring:message code="newloan.guarantors.modeNumber" /></option>
                    <option value="name"><spring:message code="newloan.guarantors.modeName" /></option>
                </select>
                <input id="guarantorSearch" type="text" autocomplete="off" placeholder="<spring:message code='newloan.guarantors.placeholder' />"
                       class="fcms-control loan-guarantor-search-input"
                       aria-describedby="guarantorHint guarantorFormatMessage" />
                <span id="guarantorFormatIndicator"
                      class="loan-guarantor-format-indicator"
                      aria-hidden="true"></span>
                <button id="guarantorSearchButton" type="button" class="app-btn btn-primary loan-guarantor-search-action">
                    <span class="loan-guarantor-search-spinner hidden" data-guarantor-search-spinner aria-hidden="true">
                        <span></span>
                        <span></span>
                        <span></span>
                    </span>
                    <span><spring:message code="common.search" /></span>
                </button>
                <div id="guarantorDropdown" class="loan-guarantor-dropdown hidden"></div>
        </div>

            <p id="guarantorFormatMessage" class="loan-guarantor-format-message hidden" aria-live="polite"></p>
           
            <p id="guarantorHint" class="mt-2 text-sm text-slate-500"></p>

            <div id="selectedGuarantors" class="loan-selected-guarantors" aria-live="polite">
                <c:forEach items="${selectedGuarantorItems}" var="item">
                    <button type="button"
                            class="selected-guarantor-chip"
                            data-id="${item.id}"
                            data-selection-key="${item.selectionKey}"
                            data-selection-token="${fn:escapeXml(item.selectionToken)}"
                            data-source="${item.source}"
                            data-local-member-id="${item.localMemberId}"
                            data-member-no="${item.memberNo}"
                            data-station-id="${item.stationId}"
                            data-full-name="${item.fullName}"
                            data-email="${item.email}"
                            data-phone="${item.phone}"
                            data-lookup-by="${item.lookupBy}"
                            data-lookup-value="${item.lookupValue}"
                            aria-label="Remove ${item.fullName}">
                        <span>${item.memberNo} - ${item.fullName}</span>
                        <span class="selected-guarantor-remove" aria-hidden="true">&times;</span>
                    </button>
                </c:forEach>
        </div>
            <div id="selectedGuarantorInputs">
                <c:forEach items="${selectedGuarantorItems}" var="item">
                    <input type="hidden"
                           name="guarantorSelections"
                           value="${fn:escapeXml(not empty item.localMemberId ? item.localMemberId : (not empty item.selectionToken ? item.selectionToken : item.id))}" />
                </c:forEach>
            </div>
            <input type="hidden" id="selectedGuarantorState" name="guarantorSelectionState" value="" />
            <c:if test="${not empty guarantorValidationErrorId}">
                <div id="guarantorValidationErrorMarker"
                     hidden
                     data-guarantor-id="${fn:escapeXml(guarantorValidationErrorId)}"
                     data-message="${fn:escapeXml(guarantorValidationErrorMessage)}"></div>
            </c:if>
        </div>
    </c:if>

    <c:if test="${requiredGuarantors le 0}">
        <div class="loan-empty-state" role="status">
            This loan product does not require a guarantor. Continue to attachments.
        </div>
    </c:if>

            </div>
        <div class="loan-flow-navigation">
            <button type="button" class="app-btn btn-neutral" data-loan-step-back>Back</button>
            <button type="button" class="app-btn btn-primary" data-loan-step-next>Continue to Attachments</button>
        </div>
    </section>

    <section class="loan-flow-panel" data-loan-flow-step="4" aria-labelledby="loanFlowStep4Title" hidden>
        <div class="loan-flow-panel-heading">
            <span class="loan-flow-step-number">4</span>
            <div>
                <h2 id="loanFlowStep4Title">Attachments</h2>
                <p>Add the documents required for this loan product.</p>
            </div>
        </div>
        <div class="loan-flow-panel-body">
    <div class="loan-workflow-section loan-attachments-section">
        <div class="mb-2 flex flex-wrap items-start justify-between gap-2">
            <div>
                <h5 class="erp-panel-title"><spring:message code="newloan.attachments.title" /></h5>
                                </div>
            <span class="app-badge loan-requirement-badge ${product.applicantAttachmentRequired ? 'is-required' : ''}">
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
        <c:if test="${not empty existingApplicationAttachments}">
            <div class="erp-table-wrap" data-aws-table-region data-aws-no-refresh="true" data-loading-label="Loading attachments...">
                <div class="app-table-titlebar">
                    <div class="app-table-heading"><h2><spring:message code="loan.attachments.existing" text="Existing Attachments" /></h2></div>
                </div>
                <div class="erp-table-scroll">
                    <table class="erp-table">
                        <thead>
                        <tr>
                            <th class="px-3 py-2 text-left"><spring:message code="loan.attachments.file" text="File" /></th>
                            <th class="px-3 py-2 text-left"><spring:message code="loan.attachments.size" text="Size" /></th>
                            <th class="px-3 py-2 text-left"><spring:message code="loan.attachments.uploaded" text="Uploaded" /></th>
                            <th class="px-3 py-2 text-left"><spring:message code="common.actions" text="Actions" /></th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${existingApplicationAttachments}" var="file">
                            <tr>
                                <td class="px-3 py-2"><c:out value="${file.originalName}" /></td>
                                <td class="px-3 py-2"><c:out value="${file.sizeLabel}" /></td>
                                <td class="px-3 py-2">
                                    <c:choose>
                                        <c:when test="${not empty file.uploadedAt}">${fn:replace(fn:substring(file.uploadedAt, 0, 16), 'T', ' ')}</c:when>
                                        <c:otherwise>-</c:otherwise>
                                    </c:choose>
                                </td>
                                <td class="px-3 py-2 whitespace-nowrap">
                                    <div class="flex flex-wrap gap-2">
                                        <a href="/documents/loan-applications/${formValues['applicationId']}/attachments/${file.id}"
                                           class="app-btn btn-primary"
                                           data-download-action="true"><spring:message code="common.download" text="Download" /></a>
                                        <button type="submit"
                                                form="removeAttachment-${file.id}"
                                                formnovalidate
                                                class="app-btn btn-reject">
                                            <spring:message code="common.remove" text="Remove" />
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </div>
        </c:if>
        <c:choose>
            <c:when test="${not empty requiredAttachmentDefinitions}">
                <div class="grid gap-3">
                    <c:forEach items="${requiredAttachmentDefinitions}" var="requirement">
                        <c:set var="existingAttachmentNames" value="${existingRequiredAttachmentNames[requirement.id]}" />
                        <div class="loan-attachment-requirement">
                            <div class="mb-2 flex flex-wrap items-start justify-between gap-2">
                                <div>
                                    <p class="text-sm font-semibold text-slate-900"><c:out value="${requirement.attachmentName}" /></p>
                                    <p class="text-xs text-slate-500">Maximum file size: ${requirement.maxSizeMb} MB</p>
                        </div>
                                <c:if test="${existingRequiredAttachmentIds.contains(requirement.id.toString())}">
                                    <span class="app-badge loan-uploaded-badge">Already uploaded</span>
                                </c:if>
                </div>
                            <label class="attachment-dropzone" data-attachment-dropzone>
                                <input type="file" name="requiredAttachmentFiles_${requirement.id}" class="attachment-dropzone-input" data-attachment-input />
                                <span class="attachment-dropzone-main">
                                    <span class="attachment-dropzone-copy">
                                        <span class="attachment-dropzone-title"><spring:message code="attachments.dropzone.titleOne" text="Drop file here" /></span>
                                        <span class="attachment-dropzone-files" data-attachment-files data-existing-attachment-files="${fn:escapeXml(existingAttachmentNames)}">
                                            <c:choose>
                                                <c:when test="${not empty existingAttachmentNames}"><c:out value="${existingAttachmentNames}" /></c:when>
                                                <c:otherwise><spring:message code="attachments.dropzone.none" text="No file selected" /></c:otherwise>
                                            </c:choose>
                                        </span>
                                    </span>
                                    <span class="attachment-dropzone-action"><spring:message code="attachments.dropzone.choose" text="Choose files" /></span>
                                </span>
                            </label>
    </div>
                    </c:forEach>
        </div>
            </c:when>
            <c:otherwise>
                <label class="attachment-dropzone" data-attachment-dropzone>
                    <input type="file" name="attachments" multiple class="attachment-dropzone-input" data-attachment-input />
                    <span class="attachment-dropzone-main">
                        <span class="attachment-dropzone-copy">
                            <span class="attachment-dropzone-title"><spring:message code="attachments.dropzone.title" text="Drop files here" /></span>
                            <span class="attachment-dropzone-files" data-attachment-files data-existing-attachment-files="${fn:escapeXml(existingApplicationAttachmentNames)}">
                                <c:choose>
                                    <c:when test="${not empty existingApplicationAttachmentNames}"><c:out value="${existingApplicationAttachmentNames}" /></c:when>
                                    <c:otherwise><spring:message code="attachments.dropzone.none" text="No file selected" /></c:otherwise>
                                </c:choose>
                            </span>
                        </span>
                        <span class="attachment-dropzone-action"><spring:message code="attachments.dropzone.choose" text="Choose files" /></span>
                    </span>
                </label>
            </c:otherwise>
        </c:choose>
        <c:if test="${not product.applicantAttachmentRequired}">
            <p class="mt-2 text-sm text-slate-500">
                <span class="attachment-dropzone-help"><spring:message code="attachments.dropzone.help" text="Images and documents can be attached." /></span>
            </p>
        </c:if>
        </div>

            </div>
        <div class="loan-flow-navigation">
            <button type="button" class="app-btn btn-neutral" data-loan-step-back>Back</button>
            <button type="button" class="app-btn btn-primary" data-loan-step-next>Continue to Save and Submit</button>
        </div>
    </section>

    <section class="loan-flow-panel" data-loan-flow-step="5" aria-labelledby="loanFlowStep5Title" hidden>
        <div class="loan-flow-panel-heading">
            <span class="loan-flow-step-number">5</span>
            <div>
                <h2 id="loanFlowStep5Title">Save and Submit</h2>
                <p>Review the declaration, save the draft, or submit the application.</p>
                        </div>
                            </div>
        <div class="loan-flow-panel-body">
    <c:if test="${requiredGuarantors le 0}">
    <div class="loan-workflow-section loan-submit-section">
        <h5 class="erp-panel-title"><spring:message code="newloan.declaration.title" /></h5>
        <div class="loan-declaration-panel">
            <p><spring:message code="newloan.declaration.text" />
                <strong>
                    <c:choose>
                        <c:when test="${not empty currentMember and not empty currentMember.fullName}">${currentMember.fullName}</c:when>
                        <c:otherwise><spring:message code="newloan.declaration.applicantFallback" /></c:otherwise>
                    </c:choose>
                </strong><spring:message code="newloan.declaration.textTail" arguments="${declarationSaccoName}" />
            </p>
            <label class="loan-confirmation-check">
                <input id="loanTermsAccepted" type="checkbox" name="termsAccepted" value="true" class="mt-1 h-4 w-4 rounded border-slate-300 text-sacco-blue focus:ring-sacco-blue" />
                <span><spring:message code="loan.terms.accept" text="I accept the terms and conditions for this loan application." /></span>
            </label>
            <div class="loan-otp-panel">
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
                        <div id="applicantSignatureOtpFeedback" data-auto-scroll-message="true" class="mt-3 hidden border px-4 py-3 text-sm"></div>
                        <div class="mt-3">
                            <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="newloan.otp.codeLabel" /></label>
                            <input type="text" name="applicantSignatureOtpCode" id="applicantSignatureOtpCode"
                                   inputmode="numeric" maxlength="6" autocomplete="one-time-code"
                                   data-otp-hidden="true" data-otp-label="Applicant OTP code"
                                   class="fcms-control loan-otp-input"
                                   placeholder="123456" />
                            <p class="mt-2 text-sm text-slate-500"><spring:message code="newloan.otp.codeHelp" /></p>
                            <div id="applicantSignatureOtpLiveStatus" class="mt-3 hidden items-center gap-2 border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
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
        <button type="button" class="app-btn btn-neutral" data-loan-step-back>Back</button>
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
        </div>
    </section>
</form>

<c:if test="${not empty existingApplicationAttachments}">
    <c:forEach items="${existingApplicationAttachments}" var="file">
        <form id="removeAttachment-${file.id}"
              action="/app/loan-applications/${formValues['applicationId']}/attachments/${file.id}/delete"
              method="post"
              class="hidden"
              data-page-preloader="true"
              data-confirm-title="Remove Attachment"
              data-confirm-message="Remove this attachment from the application?"
              data-confirm-proceed="Remove">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        </form>
    </c:forEach>
</c:if>

<script>
    (function () {
        const form = document.getElementById("loanApplicationForm");
        const panels = Array.from(form.querySelectorAll("[data-loan-flow-step]"));
        const stepButtons = Array.from(form.querySelectorAll("[data-loan-step-button]"));
        if (!panels.length || !stepButtons.length) {
            return;
        }

        const maxStep = Math.max.apply(null, panels.map(function (panel) {
            return Number(panel.dataset.loanFlowStep);
        }));
        const requestedStep = Number(new URLSearchParams(window.location.search).get("step"));
        const configuredInitialStep = Number("${loanFormInitialStep}");
        let currentStep = Number.isInteger(requestedStep) && requestedStep >= 1 && requestedStep <= maxStep
            ? requestedStep
            : (Number.isInteger(configuredInitialStep) && configuredInitialStep >= 1 && configuredInitialStep <= maxStep
                ? configuredInitialStep
                : (document.getElementById("guarantorValidationErrorMarker") ? 3 : 1));

        function panelFor(step) {
            return panels.find(function (panel) {
                return Number(panel.dataset.loanFlowStep) === Number(step);
            });
        }

        function activateStep(step, moveFocus) {
            const target = panelFor(step);
            if (!target) {
                return;
            }
            currentStep = Number(step);
            panels.forEach(function (panel) {
                panel.hidden = panel !== target;
            });
            stepButtons.forEach(function (button) {
                const buttonStep = Number(button.dataset.loanStepButton);
                const active = buttonStep === currentStep;
                button.classList.toggle("is-current", active);
                button.classList.toggle("is-complete", buttonStep < currentStep);
                if (active) {
                    button.setAttribute("aria-current", "step");
                } else {
                    button.removeAttribute("aria-current");
                }
            });
            if (moveFocus) {
                target.scrollIntoView({ behavior: "smooth", block: "start" });
                window.setTimeout(function () {
                    target.querySelector("input:not([type='hidden']), select, textarea, button")?.focus({ preventScroll: true });
                }, 220);
            }
        }

        function validateCurrentStep() {
            const panel = panelFor(currentStep);
            const controls = Array.from(panel.querySelectorAll("input, select, textarea"));
            const invalid = controls.find(function (control) {
                return !control.disabled && typeof control.checkValidity === "function" && !control.checkValidity();
            });
            if (!invalid) {
                return true;
            }
            invalid.reportValidity();
            invalid.focus();
            return false;
        }

        stepButtons.forEach(function (button) {
            button.addEventListener("click", function () {
                const targetStep = Number(button.dataset.loanStepButton);
                if (targetStep > currentStep && !validateCurrentStep()) {
                    return;
                }
                activateStep(targetStep, true);
            });
        });

        form.querySelectorAll("[data-loan-step-next]").forEach(function (button) {
            button.addEventListener("click", function () {
                if (validateCurrentStep()) {
                    activateStep(Math.min(maxStep, currentStep + 1), true);
                }
            });
        });

        form.querySelectorAll("[data-loan-step-back]").forEach(function (button) {
            button.addEventListener("click", function () {
                activateStep(Math.max(1, currentStep - 1), true);
            });
        });

        form.addEventListener("invalid", function (event) {
            const containingPanel = event.target.closest("[data-loan-flow-step]");
            if (containingPanel && containingPanel.hidden) {
                activateStep(Number(containingPanel.dataset.loanFlowStep), false);
            }
        }, true);

        window.SaccosLoanForm = window.SaccosLoanForm || {};
        window.SaccosLoanForm.activateStep = activateStep;

        activateStep(currentStep, false);
    })();
</script>

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
        const msgLoanDetailsLoaded = "<spring:message code='newloan.js.loanDetailsLoaded' text='Loan calculations loaded successfully.' />";
        const msgFailedLoadLoanDetails = "<spring:message code='newloan.js.failedLoadLoanDetails' text='Failed to load the loan calculations.' />";
        const msgSendOtp = "<spring:message code='newloan.js.sendOtp' />";
        const msgSending = "<spring:message code='newloan.js.sending' />";
        const msgOtpSent = "<spring:message code='newloan.js.otpSent' />";
        const msgSelectGuarantorsDraft = "<spring:message code='newloan.js.selectGuarantorsDraft' />";
        const msgLoanAmountChanged = "<spring:message code='newloan.js.loanAmountChanged' />";
        const msgLoadFinancialDetails = "<spring:message code='newloan.loanDetails.loading' />";
        const tableSectionLabel = "<spring:message code='newloan.table.section' text='Section' />";
        const tableValueLabel = "<spring:message code='newloan.table.value' text='Value' />";
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

        function submitButtonForAction(action) {
            return action ? form.querySelector("button[type='submit'][data-form-action='" + action + "']") : null;
        }

        function resolveSubmitButton(event) {
            const submitter = event.submitter || document.activeElement;
            if (submitter && submitter.form === form && submitter.type === "submit") {
                return submitter;
            }
            const pendingAction = form.dataset.pendingFormAction || (actionInput ? actionInput.value : "");
            return submitButtonForAction(pendingAction) || form.querySelector("button[type='submit']");
        }

        form.querySelectorAll("button[type='submit'][data-form-action]").forEach(function (button) {
            button.addEventListener("click", function () {
                form.dataset.pendingFormAction = button.dataset.formAction || "";
                if (actionInput) {
                    actionInput.value = form.dataset.pendingFormAction || "SAVE_DRAFT";
                }
            });
        });

        function normalizeMoneyInput(value) {
            const cleaned = (value || "").replace(/,/g, "").replace(/[^\d.]/g, "");
            if (!cleaned) {
                return "";
            }
            const parts = cleaned.split(".");
            const integerPart = parts.shift() || "";
            const hasDecimal = cleaned.includes(".");
            const decimalPart = parts.join("").slice(0, 2);
            if (hasDecimal) {
                return (integerPart || "0") + "." + decimalPart;
            }
            return integerPart;
        }

        function trimMoneyDecimalZeros(value) {
            if (!value || !value.includes(".")) {
                return value || "";
            }
            return value.replace(/(\.\d*?)0+$/, "$1").replace(/\.$/, "");
        }

        function formatMoneyInputValue(value, trimDecimals) {
            const normalized = normalizeMoneyInput(value);
            if (!normalized) {
                return "";
            }
            const displayValue = trimDecimals ? trimMoneyDecimalZeros(normalized) : normalized;
            const pieces = displayValue.split(".");
            const whole = pieces[0].replace(/^0+(?=\d)/, "") || "0";
            const withCommas = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
            return pieces.length > 1 ? withCommas + "." + pieces[1] : withCommas;
        }

        function caretPositionAfterMoneyFormat(rawValue, rawCaret, formattedValue) {
            const valueBeforeCaret = rawValue.slice(0, rawCaret);
            const decimalIndexBeforeCaret = valueBeforeCaret.indexOf(".");
            if (decimalIndexBeforeCaret >= 0) {
                const formattedDecimalIndex = formattedValue.indexOf(".");
                if (formattedDecimalIndex >= 0) {
                    const decimalDigitsBeforeCaret = valueBeforeCaret.slice(decimalIndexBeforeCaret + 1).replace(/\D/g, "").length;
                    return Math.min(formattedDecimalIndex + 1 + decimalDigitsBeforeCaret, formattedValue.length);
                }
            }

            const digitsBeforeCaret = valueBeforeCaret.replace(/\D/g, "").length;
            if (digitsBeforeCaret <= 0) {
                return 0;
            }
            let seenDigits = 0;
            for (let index = 0; index < formattedValue.length; index++) {
                if (/\d/.test(formattedValue.charAt(index))) {
                    seenDigits++;
                }
                if (seenDigits >= digitsBeforeCaret) {
                    return index + 1;
                }
            }
            return formattedValue.length;
        }

        function syncAmountInput(preserveCaret, trimDecimals) {
            const rawValue = amountDisplayInput.value;
            const rawCaret = typeof amountDisplayInput.selectionStart === "number"
                ? amountDisplayInput.selectionStart
                : rawValue.length;
            const normalized = normalizeMoneyInput(amountDisplayInput.value);
            const formatted = formatMoneyInputValue(normalized, trimDecimals);
            amountInput.value = normalized;
            amountDisplayInput.value = formatted;
            if (preserveCaret && document.activeElement === amountDisplayInput) {
                const nextCaret = caretPositionAfterMoneyFormat(rawValue, rawCaret, formatted);
                amountDisplayInput.setSelectionRange(nextCaret, nextCaret);
            }
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
            financialCard.innerHTML = "";
            financialCard.classList.add("hidden");
            renderRepaymentSchedule([]);
            document.dispatchEvent(new CustomEvent("loanFinancialSnapshotChanged"));
            showFinancialFeedback("error", msgLoanAmountChanged);
        }

        function renderFinancialSections(sections) {
            financialCard.innerHTML = "";
            Object.entries(sections || {}).forEach(function (section) {
                const wrapper = document.createElement("div");
                wrapper.className ="erp-table-wrap loan-summary-table";
                wrapper.setAttribute("data-aws-table-region", "");
                wrapper.setAttribute("data-aws-no-titlebar", "true");
                wrapper.setAttribute("data-aws-no-refresh", "true");
                wrapper.innerHTML = ""
                    +"<div class='app-table-titlebar'>"
                    +"<div class='app-table-heading'><h3></h3></div>"
                    + "</div>"
                    +"<div class='erp-table-scroll erp-table-scroll-sm'><table class='erp-table'>"
                    + "<thead><tr><th></th><th></th></tr></thead>"
                    + "<tbody></tbody>"
                    +"</table></div>";
                wrapper.querySelector("h3").textContent = section[0];
                const headerCells = wrapper.querySelectorAll("th");
                headerCells[0].textContent = tableSectionLabel;
                headerCells[1].textContent = tableValueLabel;
                const body = wrapper.querySelector("tbody");
                Object.entries(section[1] || {}).forEach(function (entry) {
                    const row = document.createElement("tr");
                    row.innerHTML = "<td class='px-3 py-2 font-medium text-slate-700'></td><td class='px-3 py-2'></td>";
                    row.children[0].textContent = entry[0].replace(/\bPrincipal\b/g, "Loan Amount");
                    row.children[1].textContent = entry[1];
                    body.appendChild(row);
                });
                financialCard.appendChild(wrapper);
            });
            financialCard.classList.toggle("hidden", financialCard.children.length === 0);
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
            signatureOtpFeedback.classList.remove("hidden","border-emerald-200","bg-emerald-50","text-emerald-700","border-sacco-brown/30","bg-[#f7efe9]","text-sacco-brown"
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
                const response = await fetch("/app/loan-applications/external-eligibility-summary?loanProductId=${loanProductId}", {
                    headers: {"Accept":"application/json","X-Requested-With":"XMLHttpRequest"
                    }
                });
                if (!response.ok) {
                    throw new Error(msgUnableLoadSavings);
                }
                const payload = await response.json();
                if (payload.savingsLabel) {
                    eligibilitySavingsValue.textContent = payload.savingsLabel;
                }
                if (eligibilityMaxAllowedValue && payload.maxAllowedLabel && payload.savingsLimitCheckRequired !== false) {
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

        amountDisplayInput.addEventListener("input", function () {
            syncAmountInput(true, false);
        });
        amountDisplayInput.addEventListener("change", function () {
            syncAmountInput(false, true);
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
        syncAmountInput(false, true);
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
                    headers: {"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","Accept":"application/json"
                    },
                    body: new URLSearchParams({"${_csrf.parameterName}": csrfInput.value,"applicationId": applicationId,"otpCode": code
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
                    headers: {"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","Accept":"application/json"
                    },
                    body: new URLSearchParams({"${_csrf.parameterName}": csrfInput.value,"loanProductId":"${loanProductId}","loanType":"${loanType}","amount": amountInput.value,"tenorMonths": tenorInput.value,"applicationId":"${formValues['applicationId']}","topUpLoanId": topUpLoanIdInput ? topUpLoanIdInput.value :""
                    })
                });

                if (!response.ok) {
                    throw new Error(await readJsonErrorMessage(response, msgUnableLoadOfficialDetails));
                }

                const payload = await response.json();
                financialSnapshotInput.value = payload.snapshotJson || "";
                document.dispatchEvent(new CustomEvent("loanFinancialSnapshotChanged", { detail: payload }));
                renderFinancialSections(payload.fieldSections || {});
                renderRepaymentSchedule(payload.repaymentSchedule || []);
                showFinancialFeedback("success", payload.message || msgLoanDetailsLoaded);
            } catch (error) {
                showFinancialFeedback("error", error.message || msgFailedLoadLoanDetails);
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
                        headers: {"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","Accept":"application/json"
                        },
                        body: new URLSearchParams({"${_csrf.parameterName}": csrfInput.value,"applicationId":"${formValues['applicationId']}"
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
            const submitButton = resolveSubmitButton(event);
            if (!submitButton || submitButton.disabled) {
                return;
            }
            const formAction = submitButton.dataset.formAction || form.dataset.pendingFormAction || "SAVE_DRAFT";
            form.dataset.pendingFormAction = formAction;
            if (actionInput) {
                actionInput.value = formAction;
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
            delete form.dataset.pendingFormAction;
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
                purpose.setCustomValidity("<spring:message code='newloan.purpose.maxWords' text='Use 10 words or fewer.' />");
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
            const searchButtonSpinner = searchButton ? searchButton.querySelector("[data-guarantor-search-spinner]") : null;
            const formatIndicator = document.getElementById("guarantorFormatIndicator");
            const formatMessage = document.getElementById("guarantorFormatMessage");
            const dropdown = document.getElementById("guarantorDropdown");
            const hint = document.getElementById("guarantorHint");
            const counter = document.getElementById("guarantorSelectedCount");
            const selectedContainer = document.getElementById("selectedGuarantors");
            const hiddenInputs = document.getElementById("selectedGuarantorInputs");
            const selectionStateInput = document.getElementById("selectedGuarantorState");
            const actionInput = document.getElementById("loanFormAction");
            const approvalModeInput = document.getElementById("guarantorApprovalMode");
            const approvalModeButtons = Array.from(document.querySelectorAll("[data-guarantor-approval-mode-option]"));
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
            const msgEnterGuarantorPhone = "<spring:message code='newloan.js.enterGuarantorPhone' text='Enter a Tanzania phone number, for example 255746359369 or 0746359369.' />";
            const msgEnterGuarantorEmail = "<spring:message code='newloan.js.enterGuarantorEmail' text='Enter a valid email address to search.' />";
            const msgValidGuarantorSearch = "<spring:message code='newloan.js.validGuarantorSearch' text='Ready to search.' />";
            const msgMatchingMembers = "<spring:message code='newloan.js.matchingMembers' />";
            const msgUnableSearchGuarantors = "<spring:message code='newloan.js.unableSearchGuarantors' />";
            const msgMemberDirectoryUnavailable = "<spring:message code='newloan.js.memberDirectoryUnavailable' text='Member directory is unavailable right now. Please try again later.' />";
            const msgSelectGuarantorsDraft = "<spring:message code='newloan.js.selectGuarantorsDraft' />";
            const msgDirectOtpSearchModeChanged = "<spring:message code='newloan.js.directOtpSearchModeChanged' text='Select guarantors again for this approval mode.' />";
            const modeNumberLabel = "<spring:message code='newloan.guarantors.modeNumber' />";
            const modeNameLabel = "<spring:message code='newloan.guarantors.modeName' />";
            const modePhoneLabel = "<spring:message code='newloan.guarantors.modePhone' text='Phone Number' />";
            const modeEmailLabel = "<spring:message code='newloan.guarantors.modeEmail' text='Email' />";
            const numberPlaceholder = "<spring:message code='newloan.guarantors.placeholder' />";
            const namePlaceholder = "<spring:message code='newloan.guarantors.namePlaceholder' />";
            const phonePlaceholder = "<spring:message code='newloan.guarantors.phonePlaceholder' text='Enter guarantor phone number' />";
            const emailPlaceholder = "<spring:message code='newloan.guarantors.emailPlaceholder' text='Enter guarantor email address' />";
            const numberHint = "<spring:message code='newloan.guarantors.numberHint' />";
            const nameHint = "<spring:message code='newloan.guarantors.nameHint' />";
            const phoneHint = "<spring:message code='newloan.guarantors.phoneHint' text='Use Tanzania format 255XXXXXXXXX or 0XXXXXXXXX, for example 255746359369.' />";
            const emailHint = "<spring:message code='newloan.guarantors.emailHint' text='Direct OTP searches the member profile portal by email address.' />";
            const searchConfigurations = {
                LOGIN: [
                    { value: "number", label: modeNumberLabel, placeholder: numberPlaceholder, hint: numberHint },
                    { value: "name", label: modeNameLabel, placeholder: namePlaceholder, hint: nameHint }
                ],
                DIRECT_OTP: [
                    { value: "phone", label: modePhoneLabel, placeholder: phonePlaceholder, hint: phoneHint },
                    { value: "email", label: modeEmailLabel, placeholder: emailPlaceholder, hint: emailHint }
                ]
            };

            function escapeHtml(value) {
                return String(value == null ? "" : value)
                    .replace(/&/g, "&amp;")
                    .replace(/</g, "&lt;")
                    .replace(/>/g, "&gt;")
                    .replace(/"/g, "&quot;")
                    .replace(/'/g, "&#039;");
            }

            function itemKey(item) {
                return item.selectionKey || item.id;
            }

            function localMemberId(item) {
                if (item.localMemberId) {
                    return item.localMemberId;
                }
                if (item.source === "LMS" && /^[0-9a-f-]{36}$/i.test(item.id || "")) {
                    return item.id;
                }
                return "";
            }

            function currentApprovalMode() {
                return approvalModeInput && approvalModeInput.value === "DIRECT_OTP" ? "DIRECT_OTP" : "LOGIN";
            }

            function searchConfigForCurrentMode() {
                return (searchConfigurations[currentApprovalMode()] || searchConfigurations.LOGIN)
                    .find(function (item) { return item.value === searchMode.value; });
            }

            function searchFormatMessage() {
                const approvalMode = currentApprovalMode();
                const mode = searchMode.value;
                if (approvalMode === "DIRECT_OTP" && mode === "email") {
                    return msgEnterGuarantorEmail;
                }
                if (approvalMode === "DIRECT_OTP") {
                    return msgEnterGuarantorPhone;
                }
                if (mode === "name") {
                    return msgEnterGuarantorName;
                }
                return msgEnterMemberNumber;
            }

            function normalizePhoneSearch(value) {
                const digits = String(value || "").replace(/[^0-9]/g, "");
                if (/^0[0-9]{9}$/.test(digits)) {
                    return "255" + digits.substring(1);
                }
                return /^255[0-9]{9}$/.test(digits) ? digits : "";
            }

            function validateSearchTerm(showEmpty) {
                const approvalMode = currentApprovalMode();
                const mode = searchMode.value;
                const raw = searchInput.value.trim();
                const emptyMessage = searchFormatMessage();
                if (!raw) {
                    return { valid: !showEmpty, empty: true, message: showEmpty ? emptyMessage : "", term: "" };
                }
                if (approvalMode === "DIRECT_OTP" && mode === "email") {
                    const email = raw.toLowerCase();
                    return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)
                        ? { valid: true, message: "", term: email }
                        : { valid: false, message: msgEnterGuarantorEmail, term: "" };
                }
                if (approvalMode === "DIRECT_OTP") {
                    const phone = normalizePhoneSearch(raw);
                    return phone
                        ? { valid: true, message: "", term: phone }
                        : { valid: false, message: msgEnterGuarantorPhone, term: "" };
                }
                if (mode === "name") {
                    const name = raw.toLowerCase();
                    return name.length >= 2
                        ? { valid: true, message: "", term: name }
                        : { valid: false, message: msgEnterGuarantorName, term: "" };
                }
                return { valid: true, message: "", term: raw.toUpperCase() };
            }

            function clearSearchValidation() {
                searchInput.classList.remove("field-error-input", "is-format-valid");
                searchInput.removeAttribute("aria-invalid");
                if (formatIndicator) {
                    formatIndicator.className = "loan-guarantor-format-indicator";
                    formatIndicator.removeAttribute("title");
                }
                if (formatMessage) {
                    formatMessage.textContent = "";
                    formatMessage.classList.add("hidden");
                }
            }

            function setSearchValidationState(result, forceMessage) {
                const hasValue = searchInput.value.trim().length > 0;
                clearSearchValidation();
                if (!hasValue && !forceMessage) {
                    return;
                }
                if (result.valid && hasValue) {
                    searchInput.classList.add("is-format-valid");
                    if (formatIndicator) {
                        formatIndicator.classList.add("is-valid");
                        formatIndicator.setAttribute("title", msgValidGuarantorSearch);
                    }
                    return;
                }
                if (!result.valid && forceMessage) {
                    searchInput.classList.add("field-error-input");
                    searchInput.setAttribute("aria-invalid", "true");
                    if (formatIndicator) {
                        formatIndicator.classList.add("is-invalid");
                        formatIndicator.setAttribute("title", result.message);
                    }
                    if (formatMessage) {
                        formatMessage.textContent = result.message;
                        formatMessage.classList.remove("hidden");
                    }
                }
            }

            function refreshSearchValidation(forceMessage) {
                const result = validateSearchTerm(forceMessage);
                setSearchValidationState(result, forceMessage);
                return result;
            }

            function configureSearchForApprovalMode(mode, preserveValue) {
                const config = searchConfigurations[mode] || searchConfigurations.LOGIN;
                const current = preserveValue ? searchMode.value : "";
                searchMode.innerHTML = "";
                config.forEach(function (item) {
                    const option = document.createElement("option");
                    option.value = item.value;
                    option.textContent = item.label;
                    searchMode.appendChild(option);
                });
                const selectedOption = config.find(function (item) { return item.value === current; }) || config[0];
                searchMode.value = selectedOption.value;
                searchInput.placeholder = selectedOption.placeholder;
                hint.textContent = selectedOption.hint;
                clearSearchValidation();
            }

            function setApprovalMode(mode) {
                const previous = currentApprovalMode();
                const normalized = mode === "DIRECT_OTP" ? "DIRECT_OTP" : "LOGIN";
                if (approvalModeInput) {
                    approvalModeInput.value = normalized;
                }
                approvalModeButtons.forEach(function (button) {
                    const selectedMode = button.dataset.guarantorApprovalModeOption === normalized;
                    button.setAttribute("aria-pressed", selectedMode ? "true" : "false");
                    button.classList.toggle("is-active", selectedMode);
                });
                configureSearchForApprovalMode(normalized, previous === normalized);
                searchInput.value = "";
                clearSearchValidation();
                hideDropdown();
                if (previous !== normalized && selected.size > 0) {
                    selected.clear();
                    renderSelected();
                    hint.textContent = msgDirectOtpSearchModeChanged;
                }
            }

            approvalModeButtons.forEach(function (button) {
                button.addEventListener("click", function () {
                    setApprovalMode(button.dataset.guarantorApprovalModeOption);
                });
            });
            setApprovalMode(approvalModeInput ? approvalModeInput.value : "LOGIN");

            function updateCounter() {
                counter.textContent = selected.size + " " + msgSelectedSuffix + " / " + required;
            }

            function selectionTokenForItem(item) {
                return item.selectionToken || item.localMemberId || item.id || "";
            }

            function putPayloadValue(payload, key, value) {
                const normalized = String(value == null ? "" : value).trim();
                if (normalized && normalized !== "-") {
                    payload[key] = normalized;
                }
            }

            function selectionPayloadForItem(item) {
                if (!item) {
                    return null;
                }
                const source = item.source === "FORESIGHT" ? "FORESIGHT" : "LMS";
                const payload = { source: source };
                const localId = item.localMemberId || (source === "LMS" && /^[0-9a-f-]{36}$/i.test(item.id || "") ? item.id : "");
                if (localId) {
                    payload.id = localId;
                }
                putPayloadValue(payload, "memberNo", item.memberNo);
                putPayloadValue(payload, "stationId", item.stationId);
                putPayloadValue(payload, "fullName", item.fullName);
                putPayloadValue(payload, "email", item.email);
                putPayloadValue(payload, "phone", item.phone);
                putPayloadValue(payload, "lookupBy", item.lookupBy);
                putPayloadValue(payload, "lookupValue", item.lookupValue);
                return payload;
            }

            function itemFromChip(chip) {
                const key = chip.dataset.selectionKey || chip.dataset.id || chip.dataset.guarantorId || "";
                if (!key) {
                    return null;
                }
                return {
                    id: chip.dataset.id || chip.dataset.guarantorId || key,
                    selectionKey: key,
                    selectionToken: chip.dataset.selectionToken || chip.dataset.localMemberId || chip.dataset.id || chip.dataset.guarantorId || key,
                    source: chip.dataset.source || "LMS",
                    localMemberId: chip.dataset.localMemberId || "",
                    memberNo: chip.dataset.memberNo || "-",
                    stationId: chip.dataset.stationId || "",
                    fullName: chip.dataset.fullName || "Guarantor",
                    email: chip.dataset.email || "",
                    phone: chip.dataset.phone || "",
                    lookupBy: chip.dataset.lookupBy || "",
                    lookupValue: chip.dataset.lookupValue || ""
                };
            }

            function syncSelectedFromRenderedChips() {
                let changed = false;
                Array.from(selectedContainer.querySelectorAll(".selected-guarantor-chip")).forEach(function (chip) {
                    const item = itemFromChip(chip);
                    if (item && !selected.has(item.selectionKey)) {
                        selected.set(item.selectionKey, item);
                        changed = true;
                    }
                });
                if (changed) {
                    updateCounter();
                }
            }

            function renderHiddenInputs() {
                syncSelectedFromRenderedChips();
                hiddenInputs.innerHTML = "";
                const selectionPayloads = Array.from(selected.values())
                    .map(selectionPayloadForItem)
                    .filter(function (payload) { return payload && (payload.id || payload.memberNo); });
                selectionPayloads.forEach(function (payload) {
                    const input = document.createElement("input");
                    input.type = "hidden";
                    input.name = "guarantorSelections";
                    input.value = JSON.stringify(payload);
                    hiddenInputs.appendChild(input);
                });
                if (selectionStateInput) {
                    selectionStateInput.value = JSON.stringify(selectionPayloads);
                }
            }

            function submitAction(event) {
                const submitter = event.submitter || document.activeElement;
                if (submitter && submitter.dataset && submitter.dataset.formAction) {
                    return submitter.dataset.formAction;
                }
                return form.dataset.pendingFormAction || (actionInput ? actionInput.value : "");
            }

            function renderSelected() {
                selectedContainer.innerHTML = "";
                Array.from(selected.values()).forEach(function (item) {
                    const chip = document.createElement("button");
                    chip.type = "button";
                    const hasServerError = serverGuarantorErrorId && item.localMemberId === serverGuarantorErrorId;
                    const selectionKey = item.selectionKey || item.id;
                    const selectionToken = selectionTokenForItem(item);
                    chip.className = "selected-guarantor-chip" + (hasServerError ? " has-error" : "");
                    chip.dataset.id = item.id || "";
                    chip.dataset.selectionKey = selectionKey || "";
                    chip.dataset.selectionToken = selectionToken || "";
                    chip.dataset.source = item.source || "LMS";
                    chip.dataset.localMemberId = item.localMemberId || "";
                    chip.dataset.memberNo = item.memberNo || "-";
                    chip.dataset.stationId = item.stationId || "";
                    chip.dataset.fullName = item.fullName || "Guarantor";
                    chip.dataset.email = item.email || "";
                    chip.dataset.phone = item.phone || "";
                    chip.dataset.lookupBy = item.lookupBy || "";
                    chip.dataset.lookupValue = item.lookupValue || "";
                    chip.dataset.guarantorId = item.localMemberId || item.id;
                    chip.setAttribute("aria-label", "Remove " + item.fullName);
                    chip.innerHTML = "<span>" + escapeHtml(item.memberNo) + " - " + escapeHtml(item.fullName) + "</span><span class='selected-guarantor-remove' aria-hidden='true'>&times;</span>";
                    chip.addEventListener("click", function () {
                        selected.delete(selectionKey);
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

            syncSelectedFromRenderedChips();
            renderSelected();
            window.setTimeout(revealServerGuarantorError, 120);

            function blockForGuarantorSelection(event) {
                const warning = msgSelectGuarantorsDraft.replace("{0}", required);
                event.preventDefault();
                renderHiddenInputs();
                window.SaccosLoanForm?.activateStep?.(3, true);
                hint.textContent = warning;
                window.showToast?.("error", warning);
            }

            function hideDropdown() {
                dropdown.classList.add("hidden");
                dropdown.innerHTML = "";
            }

            function setSearchLoading(loading) {
                if (!searchButton) {
                    return;
                }
                if (searchButtonSpinner) {
                    searchButtonSpinner.classList.toggle("hidden", !loading);
                }
                searchButton.disabled = loading;
                searchButton.classList.toggle("is-loading", loading);
                searchButton.setAttribute("aria-busy", loading ? "true" : "false");
            }

            function selectCandidate(item) {
                if (selected.size >= required) {
                    hint.textContent = msgOnlySelectGuarantors.replace("{0}", required);
                    hideDropdown();
                    return;
                }
                const key = itemKey(item);
                selected.set(key, {
                    id: item.id,
                    selectionKey: key,
                    selectionToken: item.selectionToken || item.id,
                    source: item.source || "LMS",
                    localMemberId: localMemberId(item),
                    memberNo: item.memberNo || "-",
                    stationId: item.stationId || "",
                    fullName: item.fullName || "Guarantor",
                    email: item.email || "",
                    phone: item.phone || "",
                    lookupBy: item.lookupBy || "",
                    lookupValue: item.lookupValue || ""
                });
                renderSelected();
                searchInput.value = "";
                clearSearchValidation();
                hint.textContent = msgGuarantorSelected;
                hideDropdown();
            }

            function showResults(items, emptyMessage, emptyIsError) {
                dropdown.innerHTML = "";
                if (!items.length) {
                    const empty = document.createElement("div");
                    empty.className = emptyIsError
                        ? "px-4 py-3 text-sm text-rose-700 bg-rose-50"
                        : "px-4 py-3 text-sm text-slate-500";
                    empty.textContent = emptyMessage || msgNoMatches;
                    dropdown.appendChild(empty);
                } else {
                    items.forEach(function (item) {
                        const row = document.createElement("div");
                        const eligible = item.eligible !== "false" && item.eligible !== false;
                        const directOtp = currentApprovalMode() === "DIRECT_OTP";
                        const candidateLabel = directOtp
                            ? escapeHtml(item.fullName || "Guarantor")
                            : escapeHtml(item.memberNo || "-") + " - " + escapeHtml(item.fullName || "Guarantor");
                        row.className = "border-b border-slate-100 px-4 py-3 text-sm " + (eligible ? "text-slate-700" : "bg-slate-50 text-slate-400");
                        row.innerHTML = "<div class='flex flex-wrap items-start justify-between gap-3'>"
                            + "<div><span class='block font-semibold'>" + candidateLabel + "</span>"
                            + (!eligible && item.disabledReason ? "<span class='mt-1 block text-xs text-rose-600'>" + escapeHtml(item.disabledReason) + "</span>" : "")
                            + "</div>"
                            + "<button type='button' class='app-btn " + (eligible ? "btn-primary" : "btn-neutral") + " px-3 py-1 text-xs' data-select-guarantor " + (eligible ? "" : "disabled") + ">Select</button>"
                            + "</div>";
                        row.querySelector("[data-select-guarantor]").addEventListener("click", function () {
                            if (!eligible) {
                                hint.textContent = item.disabledReason || "<spring:message code='newloan.js.guarantorDisabled' text='This guarantor is disabled by SACCO policy.' />";
                                return;
                            }
                            selectCandidate(item);
                        });
                        dropdown.appendChild(row);
                    });
                }
                dropdown.classList.remove("hidden");
            }

            async function runSearch() {
                const approvalMode = currentApprovalMode();
                const mode = searchMode.value;
                const validation = refreshSearchValidation(true);
                if (!validation.valid) {
                    hint.textContent = validation.message;
                    hideDropdown();
                    searchInput.focus();
                    return;
                }
                const term = validation.term;
                setSearchLoading(true);
                const query = new URLSearchParams({ q: term, searchBy: mode, loanProductId: "${loanProductId}", loanType: "${loanType}" });
                const endpoint = approvalMode === "DIRECT_OTP" ? "/app/guarantors/direct-otp/search" : "/app/guarantors/search";
                const unavailableMessage = approvalMode === "DIRECT_OTP" ? msgMemberDirectoryUnavailable : msgUnableSearchGuarantors;
                try {
                    const response = await fetch(endpoint + "?" + query.toString(), {
                        headers: {"X-Requested-With":"XMLHttpRequest"
                        }
                    });
                    const payload = await response.json().catch(function () { return approvalMode === "DIRECT_OTP" ? { items: [], message: unavailableMessage } : []; });
                    if (!response.ok) {
                        const message = payload.message || unavailableMessage;
                        hint.textContent = message;
                        showResults([], message, true);
                        window.showToast?.("error", message);
                        return;
                    }
                    const data = Array.isArray(payload) ? payload : (payload.items || []);
                    const filtered = data.filter(function (item) { return !selected.has(itemKey(item)); });
                    hint.textContent = filtered.length + " " + msgMatchingMembers;
                    showResults(filtered);
                } finally {
                    setSearchLoading(false);
                }
            }

            searchButton.addEventListener("click", function () {
                runSearch().catch(function () {
                    const message = currentApprovalMode() === "DIRECT_OTP" ? msgMemberDirectoryUnavailable : msgUnableSearchGuarantors;
                    hint.textContent = message;
                    showResults([], message, true);
                    window.showToast?.("error", message);
                });
            });

            searchInput.addEventListener("input", function () {
                refreshSearchValidation(false);
                hideDropdown();
            });

            searchInput.addEventListener("blur", function () {
                refreshSearchValidation(false);
            });

            searchMode.addEventListener("change", function () {
                const config = searchConfigForCurrentMode();
                searchInput.value = "";
                searchInput.placeholder = config ? config.placeholder : numberPlaceholder;
                hint.textContent = config ? config.hint : numberHint;
                clearSearchValidation();
                hideDropdown();
                searchInput.focus();
            });

            const draftButton = document.querySelector("button[type='submit'][data-form-action='SAVE_DRAFT']");
            if (draftButton) {
                draftButton.addEventListener("click", function (event) {
                    if (selected.size !== required) {
                        blockForGuarantorSelection(event);
                    }
                });
            }

            form.addEventListener("submit", function (event) {
                const action = submitAction(event);
                if (action !== "SAVE_DRAFT" && action !== "SEND_TO_GUARANTORS") {
                    return;
                }
                renderHiddenInputs();
                if (selected.size !== required) {
                    blockForGuarantorSelection(event);
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
