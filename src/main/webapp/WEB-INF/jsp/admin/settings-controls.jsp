<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.settings.breadcrumb" text="Admin Tools / Settings & Controls" /></p>
    <h1 class="erp-page-title"><spring:message code="admin.settings.title" text="Settings & Controls" /></h1>
</div>

<c:if test="${not empty openProductModalKey}">
    <div hidden data-open-product-modal="${fn:escapeXml(openProductModalKey)}"></div>
</c:if>

<c:if test="${settingsSection eq 'language'}">
    <section class="erp-panel aws-settings-panel aws-settings-panel--compact" aria-labelledby="adminLanguageSettingsTitle">
        <div class="aws-settings-header">
            <h2 id="adminLanguageSettingsTitle" class="aws-settings-title"><spring:message code="admin.settings.language.title" text="Workspace Default Language" /></h2>
        </div>
        <form action="/admin/settings-controls/language" method="post" class="aws-settings-form">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div class="aws-settings-control-row">
                <label class="aws-settings-field" for="adminSettingsLanguage">
                    <spring:message code="admin.settings.language.label" text="Default language" />
                    <select id="adminSettingsLanguage" name="defaultLanguage" class="aws-control">
                        <option value="en" ${settings.defaultLanguage ne 'sw' ? 'selected' : ''}><spring:message code="admin.settings.language.english" text="English" /></option>
                        <option value="sw" ${settings.defaultLanguage eq 'sw' ? 'selected' : ''}><spring:message code="admin.settings.language.swahili" text="Kiswahili" /></option>
                    </select>
                </label>
            </div>
            <div class="aws-settings-footer">
                <button type="submit" class="app-btn btn-primary"><spring:message code="admin.settings.language.save" text="Save Language" /></button>
            </div>
        </form>
    </section>
</c:if>

<c:if test="${settingsSection eq 'otp'}">
    <section class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
            <p class="erp-widget-title"><spring:message code="admin.settings.otp.eyebrow" text="Authentication" /></p>
            <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="admin.settings.otp.title" text="Station OTP Delivery" /></h2>
            <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.otp.subtitle" text="Choose how this station sends authentication and workflow confirmation codes." /></p>
        </div>
        <form action="/admin/settings-controls/otp-delivery" method="post" class="erp-panel-body">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div class="grid gap-3 lg:grid-cols-3">
                <label class="settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                    <input name="otpDeliveryChannel" type="radio" value="EMAIL" class="mt-1" ${stationOtpDeliveryChannel eq 'EMAIL' ? 'checked' : ''} />
                    <span>
                        <span class="block font-semibold text-slate-900"><spring:message code="admin.settings.otp.email" text="Email" /></span>
                        <span class="mt-1 block text-xs text-slate-500"><spring:message code="admin.settings.otp.emailHelp" text="Always send OTP codes to the account email." /></span>
                    </span>
                </label>
                <label class="settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                    <input name="otpDeliveryChannel" type="radio" value="SMS" class="mt-1" ${stationOtpDeliveryChannel eq 'SMS' ? 'checked' : ''} />
                    <span>
                        <span class="block font-semibold text-slate-900"><spring:message code="admin.settings.otp.smsOnly" text="SMS only" /></span>
                        <span class="mt-1 block text-xs text-slate-500"><spring:message code="admin.settings.otp.smsOnlyHelp" text="Use normal station SMS units. OTP requests stop when units are depleted." /></span>
                    </span>
                </label>
                <label class="settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                    <input name="otpDeliveryChannel" type="radio" value="SMS_WITH_EMAIL_FALLBACK" class="mt-1" ${stationOtpDeliveryChannel eq 'SMS_WITH_EMAIL_FALLBACK' ? 'checked' : ''} />
                    <span>
                        <span class="block font-semibold text-slate-900"><spring:message code="admin.settings.otp.smsFallback" text="SMS with email fallback" /></span>
                        <span class="mt-1 block text-xs text-slate-500"><spring:message code="admin.settings.otp.smsFallbackHelp" text="Try SMS first, then use email when SMS cannot be sent." /></span>
                    </span>
                </label>
            </div>
            <div class="relative mt-5 flex flex-row items-center justify-between gap-2 flex-wrap">
                <p class="text-sm text-slate-500"><spring:message code="admin.settings.otp.reserveNotice" text="The three reserved SMS alert units are never used for OTP codes." /></p>
                <button type="submit" class="app-btn btn-primary"><spring:message code="admin.settings.otp.save" text="Save OTP Delivery" /></button>
            </div>
        </form>
    </section>
</c:if>

<c:if test="${settingsSection eq 'board'}">
    <div class="erp-panel mb-4">
        <div class="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
                <p class="erp-widget-title"><spring:message code="admin.settings.approvalFlow.eyebrow" text="Approval Flow" /></p>
                <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="admin.settings.approvalFlow.title" text="Tenant Approval Configuration" /></h2>
                <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.approvalFlow.subtitle" text="Product workflows decide the review path. Final release remains manual and claim-protected." /></p>
            </div>
        </div>
        <form action="/admin/settings-controls/review-rules" method="post" class="erp-panel-body grid gap-4">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />

            <div class="grid gap-4 lg:grid-cols-2">
                <label class="flex items-start gap-3 border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                    <input name="loanOfficerReviewRequired" type="checkbox" value="true" class="mt-1" ${settings.loanOfficerReviewRequired ? 'checked' : ''} />
                    <span>
                        <span class="block font-semibold text-slate-900"><spring:message code="admin.settings.approvalFlow.loanOfficerRequired" text="Require Loan Officer Review" /></span>
                        <span class="mt-1 block text-slate-500"><spring:message code="admin.settings.approvalFlow.loanOfficerHelp" text="Sets the tenant default for products that inherit Loan Officer review." /></span>
                    </span>
                </label>
                <label class="flex items-start gap-3 border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                    <input name="boardReviewRequired" type="checkbox" value="true" class="mt-1" ${settings.boardReviewRequired ? 'checked' : ''} />
                    <span>
                        <span class="block font-semibold text-slate-900"><spring:message code="admin.settings.approvalFlow.boardRequired" text="Require Credit Committee Review" /></span>
                        <span class="mt-1 block text-slate-500"><spring:message code="admin.settings.approvalFlow.boardHelp" text="Sets the tenant default for products that inherit Credit Committee review." /></span>
                    </span>
                </label>
            </div>

            <div class="grid gap-4 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]">
                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Default Committee Reviewers Per Review
                    <input name="boardQuorum"
                           type="number"
                           min="1"
                           max="${activeBoardMemberCount gt 0 ? activeBoardMemberCount : 1}"
                           class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800"
                           value="${settings.boardQuorum}" />
                    <span class="mt-2 block text-sm font-normal normal-case tracking-normal text-slate-500">
                        Used when a workflow falls back to tenant Credit Committee settings.
                    </span>
                </label>

                <div class="border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-700">
                    <p class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="admin.settings.approvalFlow.resolved" text="Resolved Flow" /></p>
                    <div class="mt-3 flex flex-wrap items-center gap-2">
                        <c:forEach items="${approvalFlowStageLabels}" var="stage" varStatus="status">
                            <span class="rounded-full bg-white px-3 py-1 font-semibold text-slate-800 ring-1 ring-slate-200">${stage}</span>
                            <c:if test="${not status.last}">
                                <span class="text-slate-400">â†’</span>
                            </c:if>
                        </c:forEach>
                    </div>
                    <p class="mt-3 text-xs text-slate-500"><spring:message code="admin.settings.approvalFlow.examples" text="Examples: Branch Manager to Accountant to Disbursement, or Loan Officer to Credit Committee to Accountant to Disbursement." /></p>
                </div>
            </div>

            <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                <div class="border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
                    Active Loan Officers: <strong>${activeLoanOfficerCount}</strong>
                </div>
                <div class="border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
                    Active Board Members: <strong>${activeBoardMemberCount}</strong>
                </div>
                <div class="border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
                    Active Accountants: <strong>${activeAccountantCount}</strong>
                </div>
                <div class="border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
                    Active Disbursement Claim Holders: <strong>${activeDisbursementClaimHolderCount}</strong>
                </div>
            </div>

            <div class="app-modal-actions !justify-start md:justify-end">
                <button type="submit" class="app-btn btn-primary"><spring:message code="common.save" text="Save" /></button>
            </div>
        </form>
    </div>
</c:if>

<c:if test="${settingsSection eq 'guarantor'}">
    <section class="erp-panel aws-settings-panel aws-qualification-settings mb-4" aria-labelledby="qualification-settings-title">
        <div class="aws-settings-header">
            <p class="aws-settings-kicker"><spring:message code="admin.settings.qualification.eyebrow" text="Qualification Policies" /></p>
            <h2 id="qualification-settings-title" class="aws-settings-title"><spring:message code="admin.settings.qualification.title" text="Applicant And Guarantor Controls" /></h2>
        </div>
        <form action="/admin/settings-controls/qualification-policies" method="post" class="aws-settings-form aws-qualification-form">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div class="aws-settings-commandbar">
                <p class="aws-settings-description"><spring:message code="admin.settings.qualification.subtitle" text="These are general applicant and guarantor rules for this station. Product-specific guarantor savings are configured inside each loan product." /></p>
                <button type="submit" class="app-btn btn-primary aws-settings-save-action"><spring:message code="admin.settings.saveConfiguration" text="Save configuration" /></button>
            </div>
            <div class="aws-settings-grid aws-qualification-grid">
                <section class="aws-settings-group" aria-labelledby="loan-applicant-policy-title">
                    <div class="aws-settings-group-header">
                        <h3 id="loan-applicant-policy-title"><spring:message code="admin.settings.loanApplicants" text="Loan Applicants" /></h3>
                    </div>
                    <div class="aws-settings-group-body">
                        <label class="aws-settings-checkbox-row">
                            <input class="aws-settings-checkbox" name="applicantMaxDefaultedLoans" type="checkbox" value="1" ${not empty policyApplicantMaxDefaultedLoans and policyApplicantMaxDefaultedLoans gt 0 ? 'checked' : ''} />
                            <span class="aws-settings-checkbox-copy">
                                <span class="aws-settings-checkbox-title"><spring:message code="admin.settings.blockDefaultedApplicants" text="Block applicants with defaulted loans" /></span>
                                <span class="aws-settings-help"><spring:message code="admin.settings.blockDefaultedApplicantsHelp" text="If checked, one defaulted loan blocks new applications." /></span>
                            </span>
                        </label>
                    </div>
                </section>
                <section class="aws-settings-group" aria-labelledby="guarantor-policy-title">
                    <div class="aws-settings-group-header">
                        <h3 id="guarantor-policy-title"><spring:message code="loan.guarantors" text="Guarantors" /></h3>
                    </div>
                    <div class="aws-settings-group-body aws-settings-policy-grid">
                        <label class="aws-settings-checkbox-row">
                            <input class="aws-settings-checkbox" name="guarantorWithActiveLoanAllowed" type="checkbox" value="true" ${policyGuarantorWithActiveLoanAllowed ? 'checked' : ''} />
                            <span class="aws-settings-checkbox-copy">
                                <span class="aws-settings-checkbox-title"><spring:message code="admin.settings.allowActiveLoanGuarantors" text="Allow guarantors with active loans" /></span>
                                <span class="aws-settings-help"><spring:message code="admin.settings.allowActiveLoanGuarantorsHelp" text="If unchecked, members with active loans cannot guarantee." /></span>
                            </span>
                        </label>
                        <fmt:formatNumber value="${policyGuarantorMaxGuaranteedLoanAmount}" maxFractionDigits="0" groupingUsed="false" var="policyGuarantorMaxGuarantees" />
                        <label class="aws-settings-field aws-settings-field--compact">
                            Max Guarantees
                            <input name="guarantorMaxGuaranteedLoanAmount" type="number" min="0" max="15" step="1" data-number-range-max="15" class="aws-control" value="${policyGuarantorMaxGuarantees}" />
                        </label>
                        <label class="aws-settings-field aws-settings-field--compact">
                            Portfolio At Risk Days
                            <input name="portfolioAtRiskDays" type="number" min="1" max="365" step="1" data-number-range-max="365" class="aws-control" value="${policyPortfolioAtRiskDays}" />
                        </label>
                        <label class="aws-settings-checkbox-row">
                            <input class="aws-settings-checkbox" name="guarantorMaxDefaultedLoans" type="checkbox" value="1" ${not empty policyGuarantorMaxDefaultedLoans and policyGuarantorMaxDefaultedLoans gt 0 ? 'checked' : ''} />
                            <span class="aws-settings-checkbox-copy">
                                <span class="aws-settings-checkbox-title"><spring:message code="admin.settings.blockDefaultedGuarantors" text="Block guarantors with defaulted loans" /></span>
                                <span class="aws-settings-help"><spring:message code="admin.settings.blockDefaultedGuarantorsHelp" text="If checked, one defaulted loan blocks guarantee approvals." /></span>
                            </span>
                        </label>
                    </div>
                </section>
            </div>
        </form>
    </section>

</c:if>

<c:if test="${settingsSection eq 'loan'}">
    <section class="product-catalog" aria-labelledby="loan-product-catalog-title">
        <div class="product-catalog-toolbar">
            <div class="product-catalog-heading">
                <h2 id="loan-product-catalog-title">Select a loan product</h2>
            </div>
            <div class="product-catalog-actions">
                <button type="button"
                        class="app-btn btn-launch"
                        data-product-modal-open="create-product">
                    <spring:message code="admin.settings.loanProducts.add" text="Add loan product" />
                </button>
            </div>
        </div>

        <div class="product-catalog-grid">
        <c:forEach items="${products}" var="product">
            <c:set var="productManagerEnabled" value="${product.managerReviewRequired != false}" />
            <c:set var="productLoanOfficerEnabled"
                   value="${product.loanOfficerReviewRequired == true || (product.loanOfficerReviewRequired == null && settings.loanOfficerReviewRequired)}" />
            <c:set var="productWorkflowStartStage"><c:out value="${product.resolvedWorkflowStartStage}" /></c:set>
            <article class="erp-panel product-config-card" data-product-config-card>
                <div class="product-config-card-header">
                    <div class="product-config-card-heading">
                        <p class="erp-widget-title"><spring:message code="admin.settings.loanProducts.single" text="Loan Product" /></p>
                        <h3><c:out value="${product.displayName}" /></h3>
                        <p class="product-config-card-description"><c:out value="${product.displayDescription}" /></p>
                    </div>
                    <div class="product-config-card-actions">
                        <span class="product-config-status
                            ${product.status eq 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : ''}
                            ${product.status eq 'DRAFT' ? 'bg-amber-50 text-amber-700' : ''}
                            ${product.status eq 'SUSPENDED' ? 'bg-slate-100 text-slate-600' : ''}
                            ${product.status eq 'RETIRED' ? 'bg-rose-50 text-rose-700' : ''}">
                            ${product.status}
                        </span>
                    </div>
                </div>

                <div id="product-card-details-${product.id}"
                     class="product-config-card-details divide-y divide-slate-200"
                     data-product-card-details>
                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.loanProducts.displayOrder" text="Display Order" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.displayOrderHelp" text="Controls how products appear across the workspace." /></p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">${product.resolvedDisplayOrder}</p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.loanProducts.amountRange" text="Amount Range" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.amountRangeHelp" text="Minimum and maximum loan amount allowed for this product." /></p>
                        </div>
                        <p class="text-right text-base font-semibold text-slate-900">
                            <fmt:formatNumber value="${product.minimumAmount}" minFractionDigits="0" maxFractionDigits="2" />
                            <spring:message code="common.to" text="to" />
                            <c:choose>
                                <c:when test="${product.maximumAmount ne null}">
                                    <fmt:formatNumber value="${product.maximumAmount}" minFractionDigits="0" maxFractionDigits="2" />
                                </c:when>
                                <c:otherwise><spring:message code="common.notSet" text="Not set" /></c:otherwise>
                            </c:choose>
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.loanProducts.savingsMultiple" text="Loan Amount Limit By Savings" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.maximumLoanMultipleHelp" text="Maximum loan multiple allowed against member savings." /></p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">
                            <c:choose>
                                <c:when test="${product.maxLoanSavingsRatio gt 0}">
                                    Loan can be up to <fmt:formatNumber value="${product.maxLoanSavingsRatio}" maxFractionDigits="2" /> <spring:message code="admin.settings.loanProducts.savingsMultiplierSuffix" text="x of savings" />
                                </c:when>
                                <c:otherwise><spring:message code="common.notSet" text="Not set" /></c:otherwise>
                            </c:choose>
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.loanProducts.interestMethod" text="Interest Method" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.interestMethodHelp" text="Method and annual rate applied during repayment calculations." /></p>
                        </div>
                        <p class="text-right text-base font-semibold text-slate-900">
                            ${product.interestMethod}
                            <span class="block text-sm font-medium text-slate-600"><fmt:formatNumber value="${(product.interestRate ne null ? product.interestRate : 0) * 100}" maxFractionDigits="4" />%</span>
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.loanProducts.tenureRules" text="Tenure Rules" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.tenureRulesHelp" text="Minimum and maximum repayment duration allowed for this product." /></p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">${product.minimumRepaymentMonths} - ${product.maxRepaymentMonths} <spring:message code="common.months" text="month(s)" /></p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.loanProducts.guarantors" text="Guarantors" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.guarantorsHelp" text="Number of guarantors required before review moves forward." /></p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">${product.guarantorsRequired}</p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.guarantorSavingsCheck" text="Guarantor Savings Check" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.guarantorSavingsCheckHelp" text="Controls whether selected guarantors must meet this product's minimum savings." /></p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">
                            <c:choose>
                                <c:when test="${product.guarantorMinSavingsCheckRequired}"><spring:message code="common.enabled" text="Enabled" /></c:when>
                                <c:otherwise><spring:message code="common.disabled" text="Disabled" /></c:otherwise>
                            </c:choose>
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.loanProducts.activeLoanApplication" text="Application With Active Loan" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.activeLoanApplicationHelp" text="Controls whether members can apply again while a disbursed loan is still open." /></p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">
                            <c:choose>
                                <c:when test="${product.applicationWithActiveLoanAllowed}"><spring:message code="common.allowed" text="Allowed" /></c:when>
                                <c:otherwise><spring:message code="common.blocked" text="Blocked" /></c:otherwise>
                            </c:choose>
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.loanProducts.loadedFinancialData" text="Loaded Financial Data (Savings & Shares)" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.loadedFinancialDataHelp" text="Controls whether the member must load current financial data before the application can move forward." /></p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">
                            <c:choose>
                                <c:when test="${product.freshFinancialDataRequired}"><spring:message code="common.required" text="Required" /></c:when>
                                <c:otherwise><spring:message code="common.optional" text="Optional" /></c:otherwise>
                            </c:choose>
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.loanProducts.priorityFlow" text="Priority Flow" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.priorityFlowHelp" text="Saved approval order used in the product workflow preview." /></p>
                        </div>
                        <div class="flex flex-col items-end text-right">
                            <span class="workflow-order-${productManagerEnabled ? product.resolvedManagerPriority : 6} block text-sm font-semibold text-emerald-700">
                                <c:choose>
                                    <c:when test="${productManagerEnabled}">P${product.resolvedManagerPriority} Manager</c:when>
                                    <c:otherwise><spring:message code="admin.settings.managerSkipped" text="Manager Skipped" /></c:otherwise>
                                </c:choose>
                            </span>
                            <span class="workflow-order-${productLoanOfficerEnabled ? product.resolvedLoanOfficerPriority : 6} mt-1 block text-sm font-semibold ${(productLoanOfficerEnabled or productManagerEnabled) ? 'text-emerald-700' : 'text-slate-500'}">
                                <c:choose>
                                    <c:when test="${productLoanOfficerEnabled}">P${product.resolvedLoanOfficerPriority} Loan Officer</c:when>
                                    <c:otherwise><spring:message code="admin.settings.loanOfficerSkipped" text="Loan Officer Skipped" /></c:otherwise>
                                </c:choose>
                            </span>
                            <span class="workflow-order-${product.boardReviewRequired ? product.resolvedBoardPriority : 6} mt-1 block text-sm font-semibold ${product.boardReviewRequired ? 'text-emerald-700' : 'text-slate-500'}">
                                P${product.resolvedBoardPriority} Board Member ${product.boardReviewRequired ? 'Configured' : 'Skipped'}
                            </span>
                            <span class="workflow-order-${product.committeeReviewRequired ? product.resolvedCommitteePriority : 6} mt-1 block text-sm font-semibold ${product.committeeReviewRequired ? 'text-emerald-700' : 'text-slate-500'}">
                                P${product.resolvedCommitteePriority} Credit Committee ${product.committeeReviewRequired ? 'Configured' : 'Skipped'}
                            </span>
                            <span class="workflow-order-${product.accountantReviewRequired != false ? product.resolvedAccountantPriority : 6} mt-1 block text-sm font-semibold ${product.accountantReviewRequired != false ? 'text-emerald-700' : 'text-slate-500'}">
                                P${product.resolvedAccountantPriority} Accountant ${product.accountantReviewRequired != false ? 'Configured' : 'Skipped'}
                            </span>
                            <span class="workflow-order-7 mt-1 block text-sm font-semibold ${product.disbursementOfficerRequired != false ? 'text-emerald-700' : 'text-slate-500'}">
                                P5 ${product.disbursementOfficerRequired != false ? 'Disbursement/Teller Officer Required' : 'Disbursement Claim Release'}
                            </span>
                        </div>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.disbursementProof" text="Disbursement Proof" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.disbursementProofHelp" text="Controls whether a proof attachment is required before releasing this product." /></p>
                        </div>
                        <p class="text-right text-base font-semibold ${product.disbursementProofRequired != false ? 'text-emerald-700' : 'text-slate-600'}">
                            ${product.disbursementProofRequired != false ? 'Required' : 'Optional'}
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.applicantAttachments" text="Applicant Attachments" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.applicantAttachmentsHelp" text="Controls whether members must upload a supporting attachment while applying." /></p>
                        </div>
                        <p class="text-right text-base font-semibold ${product.applicantAttachmentRequired ? 'text-emerald-700' : 'text-slate-600'}">
                            ${product.applicantAttachmentRequired ? 'Required' : 'Optional'}
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.workflow.committeeRules" text="Credit Committee" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.workflow.committeeRulesHelp" text="Named credit committee reviewers assigned to this product." /></p>
                        </div>
                        <p class="text-right text-base font-semibold text-slate-900">
                            ${product.committeeReviewRequired ? product.resolvedCommitteeMinimumVotes : 0} reviewer(s)
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.loanProducts.repaymentCharges" text="Repayment & Charges" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.repaymentChargesHelp" text="Update pricing, interest treatment, and repayment duration for this product." /></p>
                        </div>
                        <p class="text-right text-base font-semibold text-slate-900">
                            <spring:message code="admin.settings.loanProducts.applicationFee" text="Application Fee (TZS)" />:
                            <fmt:formatNumber value="${product.applicationFee ne null ? product.applicationFee : settings.resolvedApplicationFee}" minFractionDigits="0" maxFractionDigits="2" />
                            <span class="block text-sm font-medium text-slate-600">
                                <spring:message code="admin.settings.loanProducts.processingFeePercent" text="Loan Processing Fee %" />:
                                <fmt:formatNumber value="${(product.processingFeeRate ne null ? product.processingFeeRate : 0) * 100}" maxFractionDigits="4" />%
                            </span>
                        </p>
                    </div>
                </div>
                <div class="product-config-card-footer">
                    <button type="button"
                            class="app-btn btn-link product-config-card-toggle"
                            aria-expanded="false"
                            aria-controls="product-card-details-${product.id}"
                            data-product-card-toggle>
                        <span data-product-card-toggle-label>See more</span>
                        <svg aria-hidden="true" viewBox="0 0 20 20" class="product-config-card-toggle-icon">
                            <path d="m5.5 7.5 4.5 4.5 4.5-4.5" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" />
                        </svg>
                    </button>
                    <div class="product-config-card-footer-actions">
                        <a class="app-btn btn-primary"
                           href="/admin/settings-controls/loan-products/${product.id}/edit">
                            Configure product
                        </a>
                    </div>
                </div>
            </article>
        </c:forEach>
        </div>
    </section>
</c:if>

<c:if test="${settingsSection eq 'loan'}">
    <div class="app-modal-overlay hidden"
         data-product-modal="application-fee">
        <div class="app-modal-panel max-w-2xl">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-widget-title"><spring:message code="admin.settings.loanControls" text="Loan Controls" /></p>
                        <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="admin.settings.editApplicationFee" text="Edit Application Fee" /></h2>
                        <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.applicationFeeHelp" text="Default fee used when a loan product has no product-specific application fee." /></p>
                    </div>
                    <button type="button" class="app-modal-close" data-product-modal-close="application-fee" aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                </div>

                <form action="/admin/settings-controls/loan-rules" method="post" class="app-modal-body" data-inline-validation-form="loan-settings">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="modalKey" value="application-fee" />

                    <c:if test="${openProductModalKey eq 'application-fee' and not empty message}">
                        <div class="mb-4 border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">${message}</div>
                    </c:if>
                    <c:if test="${openProductModalKey eq 'application-fee' and not empty error}">
                        <div class="mb-4 border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">${error}</div>
                    </c:if>
                    <c:if test="${openProductModalKey eq 'application-fee' and not empty loanSettingsFieldErrors}">
                        <div hidden data-modal-server-errors>
                            <c:forEach items="${loanSettingsFieldErrors}" var="fieldError">
                                <div data-modal-field-error="${fieldError.key}"><c:out value="${fieldError.value}" /></div>
                            </c:forEach>
                        </div>
                    </c:if>

                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Application Fee
                        <input name="applicationFee"
                               type="text"
                               inputmode="decimal"
                               data-money-input="true"
                               class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800"
                               value="${settings.resolvedApplicationFee}" />
                        <span class="mt-2 block text-sm font-normal normal-case tracking-normal text-slate-500">
                            Used as the default when a loan product does not have its own application fee.
                        </span>
                    </label>

                    <div class="app-modal-actions">
                        <button type="button"
                                class="app-btn btn-neutral"
                                data-product-modal-close="application-fee">
                            Cancel
                        </button>
                        <button type="submit" class="app-btn btn-primary"><spring:message code="common.save" text="Save" /></button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</c:if>

<c:if test="${settingsSection eq 'loan'}">
    <div class="app-modal-overlay hidden"
         data-product-modal="create-product">
        <div class="app-modal-panel max-w-6xl">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-widget-title"><spring:message code="admin.settings.addLoanProduct" text="Add Loan Product" /></p>
                        <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="admin.settings.createLoanProduct" text="Create Loan Product" /></h2>
                        <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.createLoanProductHelp" text="Set the loan name, approval path, and lending limits for this SACCO." /></p>
                    </div>
                    <button type="button" class="app-modal-close" data-product-modal-close="create-product" aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                </div>

                <form action="/admin/settings-controls/loan-products"
                      method="post"
                      class="app-modal-body product-builder-form"
                      data-inline-validation-form="loan-settings"
                      data-product-workflow-builder="true"
                      data-active-loan-officers="${activeLoanOfficerCount}"
                      data-active-chairpersons="${activeChairpersonCount}"
                      data-active-board-members="${activeBoardMemberCount}"
                      data-active-credit-committee-members="${activeCreditCommitteeMemberCount}"
                      data-active-accountants="${activeAccountantCount}"
                      data-active-disbursement-officers="${activeDisbursementOfficerCount}"
                      data-active-disbursement-claim-holders="${activeDisbursementClaimHolderCount}"
                      data-tenant-loan-officer-enabled="${settings.loanOfficerReviewRequired}"
                      data-tenant-board-enabled="${settings.boardReviewRequired}">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="modalKey" value="create-product" />
                    <input type="hidden" name="productCode" value="" data-product-code-field data-product-code-generated="true" />
                    <input type="hidden" name="workflowStartStage" value="MANAGER" data-workflow-start-stage-field />

                    <c:if test="${openProductModalKey eq 'create-product' and not empty message}">
                        <div class="border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">${message}</div>
                    </c:if>
                    <c:if test="${openProductModalKey eq 'create-product' and not empty error}">
                        <div class="border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">${error}</div>
                    </c:if>
                    <c:if test="${openProductModalKey eq 'create-product' and not empty loanSettingsFieldErrors}">
                        <div hidden data-modal-server-errors>
                            <c:forEach items="${loanSettingsFieldErrors}" var="fieldError">
                                <div data-modal-field-error="${fieldError.key}"><c:out value="${fieldError.value}" /></div>
                            </c:forEach>
                        </div>
                    </c:if>

                    <section class="product-builder-section">
                        <div class="product-builder-section-header">
                            <p class="erp-widget-title"><spring:message code="admin.settings.loanProducts.basics" text="Basics" /></p>
                            <h3 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.loanProducts.identity" text="Product Identity" /></h3>
                            <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.identityCreateHelp" text="Set the member-facing name, display order, and status for this product." /></p>
                        </div>
                        <div class="product-builder-section-body product-identity-grid">
                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.displayOrder" text="Display Order" />
                                <input name="displayOrder" type="number" min="1" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="5" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.name" text="Loan Product Name" />
                                <input name="productName" type="text" required maxlength="120" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder='<spring:message code="admin.settings.loanProducts.namePlaceholder" text="e.g. School Fees Booster" />' data-product-name-field />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.status" text="Product Status" />
                                <select name="productStatus" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                    <option value="ACTIVE" selected><spring:message code="status.active" text="Active" /></option>
                                    <option value="SUSPENDED"><spring:message code="status.suspended" text="Suspended" /></option>
                                </select>
                            </label>

                            <label class="product-identity-description block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="common.description" text="Description" />
                                <textarea name="productDescription" rows="3" required maxlength="500" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder='<spring:message code="admin.settings.loanProducts.descriptionPlaceholder" text="Short description shown to members when choosing this product." />'></textarea>
                            </label>
                        </div>
                    </section>

                    <section class="product-builder-section">
                        <div class="product-builder-section-header">
                            <p class="erp-widget-title"><spring:message code="admin.settings.loanProducts.eligibility" text="Eligibility" /></p>
                            <h3 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.loanProducts.eligibility" text="Eligibility" /></h3>
                            <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.eligibilityCreateHelp" text="Define lending limits and savings coverage for this product." /></p>
                        </div>
                        <div class="product-builder-section-body product-builder-grid two-up">
                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.minimumAmount" text="Minimum Loan Amount" />
                                <input name="minimumAmount" type="text" inputmode="decimal" required data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="0" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.maximumAmount" text="Maximum Loan Amount" />
                                <input name="maximumAmount" type="text" inputmode="decimal" data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder='<spring:message code="admin.settings.loanProducts.maximumAmountPlaceholder" text="Leave blank if there is no maximum amount" />' />
                            </label>

                            <div class="savings-ratio-grid md:col-span-2" data-savings-ratio-group>
                                <input name="maxLoanSavingsPercent" type="hidden" value="300.00" data-savings-percent />
                                <div class="savings-ratio-field block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    <spring:message code="admin.settings.loanProducts.savingsMultiple" text="Loan Amount Limit By Savings" />
                                    <div class="savings-multiplier-control mt-1">
                                        <input type="number" min="0" max="10" step="0.01" required class="rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="3" aria-label="Loan savings multiplier" data-savings-multiplier />
                                        <span class="savings-multiplier-label"><spring:message code="admin.settings.loanProducts.savingsMultiplierSuffix" text="x of savings" /></span>
                                    </div>
                                </div>
                                <label class="settings-checkbox-card flex items-start gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    <input name="savingsLimitCheckRequired" type="checkbox" value="true" checked data-savings-limit-check />
                                    <span>
                                        <span class="block font-semibold text-slate-800"><spring:message code="admin.settings.loanProducts.savingsLimitCheck" text="Check savings against loan amount" /></span>
                                        <span class="block text-xs font-normal normal-case tracking-normal text-slate-500"><spring:message code="admin.settings.loanProducts.savingsLimitCheckHelp" text="When unchecked, only the product minimum and maximum amount range is enforced." /></span>
                                    </span>
                                </label>
                            </div>
                            <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                <input name="freshFinancialDataRequired" type="checkbox" value="true" data-workflow-loaded-financial />
                                <span><spring:message code="admin.settings.workflow.requireLoadedFinancialData" text="Require Loaded Financial Data (Savings & Shares) At Submission" /></span>
                            </label>
                            <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                <input name="allowApplicationWithActiveLoan" type="checkbox" value="true" />
                                <span><spring:message code="admin.settings.workflow.allowApplicationWithActiveLoan" text="Allow Application With Active Loan" /></span>
                            </label>
                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.minRepaymentMonths" text="Min Repayment Months" />
                                <input name="minRepaymentMonths" type="number" min="1" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="1" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.maxRepaymentMonths" text="Max Repayment Months" />
                                <input name="maxRepaymentMonths" type="number" min="1" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="12" />
                            </label>
                        </div>
                    </section>

                    <section class="product-builder-section">
                        <div class="product-builder-section-header">
                            <p class="erp-widget-title"><spring:message code="admin.settings.loanProducts.repayment" text="Repayment" /></p>
                            <h3 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.loanProducts.repaymentCharges" text="Repayment & Charges" /></h3>
                            <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.repaymentChargesCreateHelp" text="Control pricing, interest treatment, and the repayment period for this product." /></p>
                        </div>
                        <div class="product-builder-section-body product-builder-grid two-up">
                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.applicationFee" text="Application Fee (TZS)" />
                                <input name="applicationFee" type="text" inputmode="decimal" required data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${settings.resolvedApplicationFee}" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.insurancePercent" text="Insurance %" />
                                <input name="insurancePercent" type="number" min="0" step="0.01" required data-trim-decimal-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="1.5" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.processingFeePercent" text="Loan Processing Fee %" />
                                <input name="processingFeePercent" type="number" min="0" step="0.01" required data-trim-decimal-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="0" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.annualInterestPercent" text="Annual Interest %" />
                                <input name="annualInterestPercent" type="number" min="0" step="0.01" required data-trim-decimal-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="10" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.amountReturnMethod" text="Interest Method" />
                                <select name="interestMethod" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                    <option value="FLAT_RATE" selected><spring:message code="admin.settings.loanProducts.flatRate" text="Flat Rate" /></option>
                                    <option value="REDUCING_BALANCE"><spring:message code="admin.settings.loanProducts.reducingBalance" text="Reducing Balance" /></option>
                                </select>
                            </label>
                        </div>
                    </section>

                    <section class="product-builder-section">
                        <div class="product-builder-section-header">
                            <p class="erp-widget-title"><spring:message code="admin.settings.workflow.title" text="Approval Workflow" /></p>
                            <h3 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.workflow.title" text="Approval Workflow" /></h3>
                            <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.workflow.subtitle" text="Configure the approval path for this loan product." /></p>
                        </div>
                        <div class="product-builder-section-body">
                            <div class="workflow-subsection mt-4">
                                <p class="workflow-subsection-title"><spring:message code="admin.settings.workflow.reviewStages" text="Review Stages" /></p>
                                <div class="workflow-table">
                                    <div class="workflow-table-head">
                                        <div><spring:message code="admin.settings.workflow.stage" text="Stage" /></div>
                                        <div><spring:message code="admin.settings.workflow.required" text="Required" /></div>
                                        <div><spring:message code="admin.settings.workflow.priority" text="Priority" /></div>
                                        <div><spring:message code="admin.settings.workflow.notes" text="Notes" /></div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.manager" text="Manager" /></div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                            <label class="workflow-checkbox-lock"><input name="managerReviewRequired" type="checkbox" value="true" checked data-workflow-manager aria-label="Manager required" /></label>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                            <select name="managerPriority" class="workflow-priority-select" data-workflow-manager-priority>
                                                <c:forEach begin="1" end="6" var="priorityOption">
                                                    <option value="${priorityOption}" ${priorityOption eq 1 ? 'selected' : ''}>${priorityOption}</option>
                                                </c:forEach>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'><spring:message code="admin.settings.workflow.managerLoanOfficerPriorityNote" text="Priority follows active review roles." /></div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.loanOfficer" text="Loan Officer" /></div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                            <label class="workflow-checkbox-lock"><input name="loanOfficerReviewRequired" type="checkbox" value="true" ${settings.loanOfficerReviewRequired ? 'checked' : ''} data-workflow-loan-officer aria-label="Loan Officer required" /></label>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                            <select name="loanOfficerPriority" class="workflow-priority-select" data-workflow-loan-officer-priority>
                                                <c:forEach begin="1" end="6" var="priorityOption">
                                                    <option value="${priorityOption}" ${priorityOption eq 2 ? 'selected' : ''}>${priorityOption}</option>
                                                </c:forEach>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                            <c:choose>
                                                <c:when test="${settings.loanOfficerReviewRequired}">
                                                    <spring:message code="admin.settings.workflow.loanOfficerPriorityNote" text="Priority follows active review roles." />
                                                </c:when>
                                                <c:otherwise>
                                                    <spring:message code="admin.settings.workflow.enableLoanOfficerNote" text="Enable Loan Officer review in tenant approval flow settings to use this stage." />
                                                </c:otherwise>
                                            </c:choose>
                                        </div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.chairperson" text="Chairperson" /></div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                            <label class="workflow-checkbox-lock"><input name="chairpersonReviewRequired" type="checkbox" value="true" data-workflow-chairperson aria-label="Chairperson required" /></label>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                            <select name="chairpersonPriority" class="workflow-priority-select" data-workflow-chairperson-priority>
                                                <c:forEach begin="1" end="6" var="priorityOption">
                                                    <option value="${priorityOption}" ${priorityOption eq 3 ? 'selected' : ''}>${priorityOption}</option>
                                                </c:forEach>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                            <spring:message code="admin.settings.workflow.openPriorityNote" text="Priority follows active review roles." />
                                        </div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.boardMember" text="Board Member" /></div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                            <label class="workflow-checkbox-lock"><input name="boardReviewRequired" type="checkbox" value="true" data-workflow-board aria-label="Board Member required" /></label>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                            <select name="boardPriority" class="workflow-priority-select" data-workflow-board-priority>
                                                <c:forEach begin="1" end="6" var="priorityOption">
                                                    <option value="${priorityOption}" ${priorityOption eq 3 ? 'selected' : ''}>${priorityOption}</option>
                                                </c:forEach>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                            <spring:message code="admin.settings.workflow.openPriorityNote" text="Priority follows active review roles." />
                                        </div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.committee" text="Credit Committee" /></div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                            <label class="workflow-checkbox-lock"><input name="committeeReviewRequired" type="checkbox" value="true" ${settings.boardReviewRequired ? 'checked' : ''} data-workflow-committee aria-label="Committee required" /></label>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                            <select name="committeePriority" class="workflow-priority-select" data-workflow-committee-priority>
                                                <c:forEach begin="1" end="6" var="priorityOption">
                                                    <option value="${priorityOption}" ${priorityOption eq 4 ? 'selected' : ''}>${priorityOption}</option>
                                                </c:forEach>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                            <spring:message code="admin.settings.workflow.openPriorityNote" text="Priority follows active review roles." />
                                        </div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.accountant" text="Accountant" /></div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                            <label class="workflow-checkbox-lock"><input name="accountantReviewRequired" type="checkbox" value="true" checked data-workflow-accountant aria-label="Accountant required" /></label>
                                            <input type="hidden" name="accountantReviewRequired" value="false" />
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                            <select name="accountantPriority" class="workflow-priority-select" data-workflow-accountant-priority>
                                                <c:forEach begin="1" end="6" var="priorityOption">
                                                    <option value="${priorityOption}" ${priorityOption eq 5 ? 'selected' : ''}>${priorityOption}</option>
                                                </c:forEach>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                            <spring:message code="admin.settings.workflow.openPriorityNote" text="Priority follows active review roles." />
                                        </div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.disbursementOfficer" text="Disbursement/Teller Officer" /></div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                            <label class="workflow-checkbox-lock"><input name="disbursementOfficerRequired" type="checkbox" value="true" checked data-workflow-disbursement-officer aria-label="Disbursement/Teller Officer required" /></label>
                                            <input type="hidden" name="disbursementOfficerRequired" value="false" />
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                            <select class="workflow-priority-select" disabled><option selected>7</option></select>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                            <spring:message code="admin.settings.workflow.disbursementOfficerNote" text="Role requirement for final manual release." />
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title"><spring:message code="role.boardMember" text="Board Member" /></p>
                                <div class="grid gap-3">
                                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                        Board Members Assigned
                                        <input type="search" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="Search board members..." data-board-reviewer-search />
                                    </label>
                                    <div class="grid max-h-56 gap-2 overflow-y-auto overscroll-contain rounded border border-slate-200 bg-slate-50 p-3 sm:max-h-64" data-board-reviewer-list>
                                        <c:forEach items="${boardReviewerOptions}" var="reviewer">
                                            <label class="settings-checkbox-card flex items-start gap-2 rounded border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700" data-board-reviewer-option data-board-reviewer-text="${fn:toLowerCase(reviewer.fullName)} ${fn:toLowerCase(reviewer.memberNo)}">
                                                <input name="boardReviewerIds" type="checkbox" value="${reviewer.id}" />
                                                <span>
                                                    <span class="block font-semibold text-slate-900">${reviewer.fullName}</span>
                                                    <span class="block text-xs text-slate-500">${reviewer.memberNo}</span>
                                                </span>
                                            </label>
                                        </c:forEach>
                                        <c:if test="${empty boardReviewerOptions}">
                                            <div class="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700">No active board members are available.</div>
                                        </c:if>
                                    </div>
                                    <p class="text-xs text-slate-500">Every selected board member will be assigned and notified for this product.</p>
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title"><spring:message code="role.committee" text="Credit Committee" /></p>
                                <div class="grid gap-3">
                                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                        Credit Committee Assigned
                                        <input type="search" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="Search credit committee..." data-credit-committee-reviewer-search />
                                    </label>
                                    <div class="grid max-h-56 gap-2 overflow-y-auto overscroll-contain rounded border border-slate-200 bg-slate-50 p-3 sm:max-h-64" data-credit-committee-reviewer-list>
                                        <c:forEach items="${creditCommitteeReviewerOptions}" var="reviewer">
                                            <label class="settings-checkbox-card flex items-start gap-2 rounded border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700" data-credit-committee-reviewer-option data-credit-committee-reviewer-text="${fn:toLowerCase(reviewer.fullName)} ${fn:toLowerCase(reviewer.memberNo)}">
                                                <input name="creditCommitteeReviewerIds" type="checkbox" value="${reviewer.id}" />
                                                <span>
                                                    <span class="block font-semibold text-slate-900">${reviewer.fullName}</span>
                                                    <span class="block text-xs text-slate-500">${reviewer.memberNo}</span>
                                                </span>
                                            </label>
                                        </c:forEach>
                                        <c:if test="${empty creditCommitteeReviewerOptions}">
                                            <div class="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700">No active credit committee members are available.</div>
                                        </c:if>
                                    </div>
                                    <p class="text-xs text-slate-500">Every selected credit committee member will be assigned and notified for this product.</p>
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title"><spring:message code="common.applicant" text="Applicant" /></p>
                                <div class="workflow-support-grid">
                                    <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                        <input name="applicantAttachmentRequired" type="checkbox" value="true" data-required-attachments-toggle />
                                        <span><spring:message code="admin.settings.requireApplicantAttachment" text="Require applicant attachment while applying" /></span>
                                    </label>
                                    <input type="hidden" name="applicantAttachmentRequired" value="false" />
                                </div>
                                <div class="mt-3 hidden border border-slate-200 bg-white p-3" data-required-attachments-panel>
                                    <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
                                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Required Attachments</p>
                                        <button type="button" class="app-btn btn-neutral" data-add-required-attachment>Add Name of Required Attachment</button>
                                    </div>
                                    <div class="grid gap-2" data-required-attachments-list></div>
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title"><spring:message code="role.disbursementOfficer" text="Disbursement/Teller Officer" /></p>
                                <div class="workflow-support-grid">
                                    <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                        <input name="disbursementProofRequired" type="checkbox" value="true" checked />
                                        <span><spring:message code="admin.settings.requireProofBeforeDisbursement" text="Require proof attachment before disbursement" /></span>
                                    </label>
                                    <input type="hidden" name="disbursementProofRequired" value="false" />
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title"><spring:message code="admin.settings.workflow.guarantorSettings" text="Guarantor Settings" /></p>
                                <div class="workflow-support-grid">
                                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                        <spring:message code="admin.settings.workflow.guarantorsRequired" text="Guarantors Required" />
                                        <input name="guarantorsRequired" type="number" min="0" max="15" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="2" data-number-range-max="15" data-workflow-guarantors />
                                    </label>
                                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                        Minimum Guarantor Savings
                                        <input name="guarantorMinimumSavings" type="text" inputmode="decimal" data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="0" />
                                    </label>
                                    <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                        <input name="guarantorMinSavingsCheckRequired" type="checkbox" value="true" />
                                        <span><spring:message code="admin.settings.checkGuarantorSavings" text="Check guarantor minimum savings before selection" /></span>
                                    </label>
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title"><spring:message code="admin.settings.workflow.validation" text="Validation" /></p>
                                <div class="workflow-warning-stack mt-4" data-workflow-warnings></div>
                            </div>
                        </div>
                    </section>

                    <section class="product-builder-section">
                        <div class="product-builder-section-header">
                            <p class="erp-widget-title"><spring:message code="admin.settings.workflow.preview" text="Preview" /></p>
                            <h3 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.workflow.resolvedFlow" text="Resolved Flow" /></h3>
                            <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.workflow.resolvedFlowHelp" text="The runtime path below reflects what the system would execute with current tenant settings." /></p>
                        </div>
                        <div class="product-builder-section-body">
                            <div class="workflow-preview-shell">
                                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="admin.settings.workflow.title" text="Approval Workflow" /></p>
                                <div class="mt-3 workflow-preview-runtime" data-workflow-preview-runtime></div>
                            </div>
                        </div>
                    </section>

                    <div class="app-modal-actions">
                        <button type="button"
                                class="app-btn btn-neutral"
                                data-product-modal-close="create-product">
                            <spring:message code="common.cancel" text="Cancel" />
                        </button>
                        <button type="submit" class="app-btn btn-launch"><spring:message code="admin.settings.loanProducts.add" text="Add loan product" /></button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</c:if>

<c:forEach items="${products}" var="product">
    <div class="app-modal-overlay hidden"
         data-product-modal="product-${product.id}">
        <div class="app-modal-panel max-w-6xl">
            <div class="app-modal-scroll">
            <div class="app-modal-header">
                <div>
                    <p class="erp-widget-title"><spring:message code="admin.settings.loanProducts.editProduct" text="Edit Product" /></p>
                    <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="admin.settings.loanProducts.editTitle" text="Edit Loan Product:" /> <c:out value="${product.displayName}" /></h2>
                </div>
                <button type="button" class="app-modal-close" data-product-modal-close="product-${product.id}" aria-label='<spring:message code="common.closeModal" text="Close modal" />'>
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                        <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                    </svg>
                </button>
            </div>

            <form action="/admin/settings-controls/${product.id}"
                  method="post"
                  class="app-modal-body product-builder-form"
                  data-inline-validation-form="loan-settings"
                  data-product-workflow-builder="true"
                  data-product-edit-mode="true"
                  data-active-loan-officers="${activeLoanOfficerCount}"
                  data-active-chairpersons="${activeChairpersonCount}"
                  data-active-board-members="${activeBoardMemberCount}"
                  data-active-credit-committee-members="${activeCreditCommitteeMemberCount}"
                  data-active-accountants="${activeAccountantCount}"
                  data-active-disbursement-officers="${activeDisbursementOfficerCount}"
                  data-active-disbursement-claim-holders="${activeDisbursementClaimHolderCount}"
                  data-tenant-loan-officer-enabled="${settings.loanOfficerReviewRequired}"
                  data-tenant-board-enabled="${settings.boardReviewRequired}">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <input type="hidden" name="modalKey" value="product-${product.id}" />
                <input type="hidden" name="productCode" value="${fn:escapeXml(product.displayCode)}" data-product-code-field />
                <input type="hidden" name="workflowStartStage" value="${product.resolvedWorkflowStartStage}" data-workflow-start-stage-field />
                <c:set var="productModalKey" value="product-${product.id}" />

                <c:if test="${openProductModalKey eq productModalKey and not empty message}">
                    <div class="border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">${message}</div>
                </c:if>
                <c:if test="${openProductModalKey eq productModalKey and not empty error}">
                    <div class="border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">${error}</div>
                </c:if>
                <c:if test="${openProductModalKey eq productModalKey and not empty loanSettingsFieldErrors}">
                    <div hidden data-modal-server-errors>
                        <c:forEach items="${loanSettingsFieldErrors}" var="fieldError">
                            <div data-modal-field-error="${fieldError.key}"><c:out value="${fieldError.value}" /></div>
                        </c:forEach>
                    </div>
                </c:if>

                <section class="product-builder-section">
                    <div class="product-builder-section-header">
                        <p class="erp-widget-title"><spring:message code="admin.settings.loanProducts.basics" text="Basics" /></p>
                        <h3 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.loanProducts.identity" text="Product Identity" /></h3>
                        <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.identityHelp" text="Update the member-facing name, display order, and status for this product." /></p>
                    </div>
                    <div class="product-builder-section-body product-identity-grid">
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.displayOrder" text="Display Order" />
                            <input name="displayOrder" type="number" min="1" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.resolvedDisplayOrder}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.name" text="Loan Product Name" />
                            <input name="productName" type="text" required maxlength="120" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${fn:escapeXml(product.displayName)}" data-product-name-field />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.status" text="Product Status" />
                            <select name="productStatus" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                <option value="ACTIVE" ${product.status ne 'SUSPENDED' ? 'selected' : ''}><spring:message code="status.active" text="Active" /></option>
                                <option value="SUSPENDED" ${product.status eq 'SUSPENDED' ? 'selected' : ''}><spring:message code="status.suspended" text="Suspended" /></option>
                            </select>
                        </label>

                        <label class="product-identity-description block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="common.description" text="Description" />
                            <textarea name="productDescription" rows="3" required maxlength="500" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder='<spring:message code="admin.settings.loanProducts.descriptionPlaceholder" text="Short description shown to members when choosing this product." />'>${fn:escapeXml(product.productDescription)}</textarea>
                        </label>
                    </div>
                </section>

                <section class="product-builder-section">
                    <div class="product-builder-section-header">
                        <p class="erp-widget-title"><spring:message code="admin.settings.loanProducts.eligibility" text="Eligibility" /></p>
                        <h3 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.loanProducts.eligibility" text="Eligibility" /></h3>
                        <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.eligibilityHelp" text="Update loan limits and savings coverage for this product." /></p>
                    </div>
                    <div class="product-builder-section-body product-builder-grid two-up">
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.minimumAmount" text="Minimum Loan Amount" />
                            <input name="minimumAmount" type="text" inputmode="decimal" required data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.minimumAmount}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.maximumAmount" text="Maximum Loan Amount" />
                            <input name="maximumAmount" type="text" inputmode="decimal" data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maximumAmount}" placeholder='<spring:message code="admin.settings.loanProducts.maximumAmountPlaceholder" text="Leave blank if there is no maximum amount" />' />
                        </label>

                        <div class="savings-ratio-grid md:col-span-2" data-savings-ratio-group>
                            <input name="maxLoanSavingsPercent" type="hidden" value="${product.maxLoanSavingsRatio * 100}" data-savings-percent />
                            <div class="savings-ratio-field block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.savingsMultiple" text="Loan Amount Limit By Savings" />
                                <div class="savings-multiplier-control mt-1">
                                    <input type="number" min="0" max="10" step="0.01" required class="rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" aria-label="Loan savings multiplier" data-savings-multiplier />
                                    <span class="savings-multiplier-label"><spring:message code="admin.settings.loanProducts.savingsMultiplierSuffix" text="x of savings" /></span>
                                </div>
                            </div>
                            <label class="settings-checkbox-card flex items-start gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                <input name="savingsLimitCheckRequired" type="checkbox" value="true" ${product.savingsLimitCheckRequired ? 'checked' : ''} data-savings-limit-check />
                                <span>
                                    <span class="block font-semibold text-slate-800"><spring:message code="admin.settings.loanProducts.savingsLimitCheck" text="Check savings against loan amount" /></span>
                                    <span class="block text-xs font-normal normal-case tracking-normal text-slate-500"><spring:message code="admin.settings.loanProducts.savingsLimitCheckHelp" text="When unchecked, only the product minimum and maximum amount range is enforced." /></span>
                                </span>
                            </label>
                        </div>
                        <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                            <input name="freshFinancialDataRequired" type="checkbox" value="true" ${product.freshFinancialDataRequired ? 'checked' : ''} data-workflow-loaded-financial />
                            <span><spring:message code="admin.settings.workflow.requireLoadedFinancialData" text="Require Loaded Financial Data (Savings & Shares) At Submission" /></span>
                        </label>
                        <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                            <input name="allowApplicationWithActiveLoan" type="checkbox" value="true" ${product.applicationWithActiveLoanAllowed ? 'checked' : ''} />
                            <span><spring:message code="admin.settings.workflow.allowApplicationWithActiveLoan" text="Allow Application With Active Loan" /></span>
                        </label>
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.minRepaymentMonths" text="Min Repayment Months" />
                            <input name="minRepaymentMonths" type="number" min="1" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.minimumRepaymentMonths}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.maxRepaymentMonths" text="Max Repayment Months" />
                            <input name="maxRepaymentMonths" type="number" min="1" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maxRepaymentMonths}" />
                        </label>
                    </div>
                </section>

                <section class="product-builder-section">
                    <div class="product-builder-section-header">
                        <p class="erp-widget-title"><spring:message code="admin.settings.loanProducts.repayment" text="Repayment" /></p>
                        <h3 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.loanProducts.repaymentCharges" text="Repayment & Charges" /></h3>
                        <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.repaymentChargesHelp" text="Update pricing, interest treatment, and repayment duration for this product." /></p>
                    </div>
                    <div class="product-builder-section-body product-builder-grid two-up">
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.applicationFee" text="Application Fee (TZS)" />
                            <input name="applicationFee" type="text" inputmode="decimal" required data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.applicationFee ne null ? product.applicationFee : settings.resolvedApplicationFee}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.insurancePercent" text="Insurance %" />
                            <input name="insurancePercent" type="number" min="0" step="0.01" required data-trim-decimal-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${(product.insuranceRate ne null ? product.insuranceRate : 0) * 100}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.processingFeePercent" text="Loan Processing Fee %" />
                            <input name="processingFeePercent" type="number" min="0" step="0.01" required data-trim-decimal-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${(product.processingFeeRate ne null ? product.processingFeeRate : 0) * 100}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.annualInterestPercent" text="Annual Interest %" />
                            <input name="annualInterestPercent" type="number" min="0" step="0.01" required data-trim-decimal-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${(product.interestRate ne null ? product.interestRate : 0) * 100}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.amountReturnMethod" text="Interest Method" />
                            <select name="interestMethod" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                <option value="FLAT_RATE" ${product.interestMethod eq 'FLAT_RATE' ? 'selected' : ''}><spring:message code="admin.settings.loanProducts.flatRate" text="Flat Rate" /></option>
                                <option value="REDUCING_BALANCE" ${product.interestMethod eq 'REDUCING_BALANCE' ? 'selected' : ''}><spring:message code="admin.settings.loanProducts.reducingBalance" text="Reducing Balance" /></option>
                            </select>
                        </label>
                    </div>
                </section>

                <section class="product-builder-section">
                    <div class="product-builder-section-header">
                        <p class="erp-widget-title"><spring:message code="admin.settings.workflow.title" text="Approval Workflow" /></p>
                        <h3 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.workflow.title" text="Approval Workflow" /></h3>
                        <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.workflow.subtitle" text="Configure the approval path for this loan product." /></p>
                    </div>
                    <div class="product-builder-section-body">
                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="admin.settings.workflow.reviewStages" text="Review Stages" /></p>
                            <div class="workflow-table">
                                <div class="workflow-table-head">
                                    <div><spring:message code="admin.settings.workflow.stage" text="Stage" /></div>
                                    <div><spring:message code="admin.settings.workflow.required" text="Required" /></div>
                                    <div><spring:message code="admin.settings.workflow.priority" text="Priority" /></div>
                                    <div><spring:message code="admin.settings.workflow.notes" text="Notes" /></div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.manager" text="Manager" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="managerReviewRequired" type="checkbox" value="true" ${product.managerReviewRequired != false ? 'checked' : ''} data-workflow-manager aria-label="Manager required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="managerPriority" class="workflow-priority-select" data-workflow-manager-priority>
                                        <c:forEach begin="1" end="6" var="priorityOption">
                                                <option value="${priorityOption}" ${product.resolvedManagerPriority eq priorityOption ? 'selected' : ''}>${priorityOption}</option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'><spring:message code="admin.settings.workflow.managerLoanOfficerPriorityNote" text="Priority follows active review roles." /></div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.loanOfficer" text="Loan Officer" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="loanOfficerReviewRequired" type="checkbox" value="true" ${(product.loanOfficerReviewRequired == true || (product.loanOfficerReviewRequired == null && settings.loanOfficerReviewRequired)) ? 'checked' : ''} data-workflow-loan-officer aria-label="Loan Officer required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="loanOfficerPriority" class="workflow-priority-select" data-workflow-loan-officer-priority>
                                        <c:forEach begin="1" end="6" var="priorityOption">
                                                <option value="${priorityOption}" ${product.resolvedLoanOfficerPriority eq priorityOption ? 'selected' : ''}>${priorityOption}</option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <c:choose>
                                            <c:when test="${product.loanOfficerReviewRequired == true || (product.loanOfficerReviewRequired == null && settings.loanOfficerReviewRequired)}">
                                                <spring:message code="admin.settings.workflow.loanOfficerPriorityNote" text="Priority follows active review roles." />
                                            </c:when>
                                            <c:otherwise>
                                                <spring:message code="admin.settings.workflow.enableLoanOfficerNote" text="Enable Loan Officer review in tenant approval flow settings to use this stage." />
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.chairperson" text="Chairperson" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="chairpersonReviewRequired" type="checkbox" value="true" ${product.chairpersonReviewRequired ? 'checked' : ''} data-workflow-chairperson aria-label="Chairperson required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="chairpersonPriority" class="workflow-priority-select" data-workflow-chairperson-priority>
                                        <c:forEach begin="1" end="6" var="priorityOption">
                                                <option value="${priorityOption}" ${product.resolvedChairpersonPriority == priorityOption ? 'selected' : ''}>${priorityOption}</option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.openPriorityNote" text="Priority follows active review roles." />
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.boardMember" text="Board Member" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="boardReviewRequired" type="checkbox" value="true" ${product.boardReviewRequired ? 'checked' : ''} data-workflow-board aria-label="Board Member required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="boardPriority" class="workflow-priority-select" data-workflow-board-priority>
                                        <c:forEach begin="1" end="6" var="priorityOption">
                                                <option value="${priorityOption}" ${product.resolvedBoardPriority == priorityOption ? 'selected' : ''}>${priorityOption}</option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.openPriorityNote" text="Priority follows active review roles." />
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.committee" text="Credit Committee" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="committeeReviewRequired" type="checkbox" value="true" ${product.committeeReviewRequired ? 'checked' : ''} data-workflow-committee aria-label="Committee required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="committeePriority" class="workflow-priority-select" data-workflow-committee-priority>
                                        <c:forEach begin="1" end="6" var="priorityOption">
                                                <option value="${priorityOption}" ${product.resolvedCommitteePriority == priorityOption ? 'selected' : ''}>${priorityOption}</option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.openPriorityNote" text="Priority follows active review roles." />
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.accountant" text="Accountant" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="accountantReviewRequired" type="checkbox" value="true" ${product.accountantReviewRequired != false ? 'checked' : ''} data-workflow-accountant aria-label="Accountant required" /></label>
                                        <input type="hidden" name="accountantReviewRequired" value="false" />
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="accountantPriority" class="workflow-priority-select" data-workflow-accountant-priority>
                                        <c:forEach begin="1" end="6" var="priorityOption">
                                                <option value="${priorityOption}" ${product.resolvedAccountantPriority == priorityOption ? 'selected' : ''}>${priorityOption}</option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.openPriorityNote" text="Priority follows active review roles." />
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.disbursementOfficer" text="Disbursement/Teller Officer" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="disbursementOfficerRequired" type="checkbox" value="true" ${product.disbursementOfficerRequired != false ? 'checked' : ''} data-workflow-disbursement-officer aria-label="Disbursement/Teller Officer required" /></label>
                                        <input type="hidden" name="disbursementOfficerRequired" value="false" />
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select class="workflow-priority-select" disabled><option selected>7</option></select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.disbursementOfficerNote" text="Role requirement for final manual release." />
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="role.boardMember" text="Board Member" /></p>
                            <div class="grid gap-3">
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    Board Members Assigned
                                    <input type="search" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="Search board members..." data-board-reviewer-search />
                                </label>
                                <c:set var="assignedBoardReviewerTokens" value="${productBoardReviewerIdTokens[product.id]}" />
                                <div class="grid max-h-56 gap-2 overflow-y-auto overscroll-contain rounded border border-slate-200 bg-slate-50 p-3 sm:max-h-64" data-board-reviewer-list>
                                    <c:forEach items="${boardReviewerOptions}" var="reviewer">
                                        <c:set var="reviewerToken" value="|${reviewer.id}|" />
                                        <label class="settings-checkbox-card flex items-start gap-2 rounded border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700" data-board-reviewer-option data-board-reviewer-text="${fn:toLowerCase(reviewer.fullName)} ${fn:toLowerCase(reviewer.memberNo)}">
                                            <input name="boardReviewerIds" type="checkbox" value="${reviewer.id}" ${fn:contains(assignedBoardReviewerTokens, reviewerToken) ? 'checked' : ''} />
                                            <span>
                                                <span class="block font-semibold text-slate-900">${reviewer.fullName}</span>
                                                <span class="block text-xs text-slate-500">${reviewer.memberNo}</span>
                                            </span>
                                        </label>
                                    </c:forEach>
                                    <c:if test="${empty boardReviewerOptions}">
                                        <div class="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700">No active board members are available.</div>
                                    </c:if>
                                </div>
                                <p class="text-xs text-slate-500">Every selected board member will be assigned and notified for this product.</p>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="role.committee" text="Credit Committee" /></p>
                            <div class="grid gap-3">
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    Credit Committee Assigned
                                    <input type="search" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="Search credit committee..." data-credit-committee-reviewer-search />
                                </label>
                                <c:set var="assignedCreditCommitteeReviewerTokens" value="${productCreditCommitteeReviewerIdTokens[product.id]}" />
                                <div class="grid max-h-56 gap-2 overflow-y-auto overscroll-contain rounded border border-slate-200 bg-slate-50 p-3 sm:max-h-64" data-credit-committee-reviewer-list>
                                    <c:forEach items="${creditCommitteeReviewerOptions}" var="reviewer">
                                        <c:set var="reviewerToken" value="|${reviewer.id}|" />
                                        <label class="settings-checkbox-card flex items-start gap-2 rounded border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700" data-credit-committee-reviewer-option data-credit-committee-reviewer-text="${fn:toLowerCase(reviewer.fullName)} ${fn:toLowerCase(reviewer.memberNo)}">
                                            <input name="creditCommitteeReviewerIds" type="checkbox" value="${reviewer.id}" ${fn:contains(assignedCreditCommitteeReviewerTokens, reviewerToken) ? 'checked' : ''} />
                                            <span>
                                                <span class="block font-semibold text-slate-900">${reviewer.fullName}</span>
                                                <span class="block text-xs text-slate-500">${reviewer.memberNo}</span>
                                            </span>
                                        </label>
                                    </c:forEach>
                                    <c:if test="${empty creditCommitteeReviewerOptions}">
                                        <div class="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700">No active credit committee members are available.</div>
                                    </c:if>
                                </div>
                                <p class="text-xs text-slate-500">Every selected credit committee member will be assigned and notified for this product.</p>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title">Applicant</p>
                            <div class="workflow-support-grid">
                                <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    <input name="applicantAttachmentRequired" type="checkbox" value="true" ${product.applicantAttachmentRequired ? 'checked' : ''} data-required-attachments-toggle />
                                    <span><spring:message code="admin.settings.requireApplicantAttachment" text="Require applicant attachment while applying" /></span>
                                </label>
                                <input type="hidden" name="applicantAttachmentRequired" value="false" />
                            </div>
                            <div class="mt-3 ${product.applicantAttachmentRequired ? '' : 'hidden '} border border-slate-200 bg-white p-3" data-required-attachments-panel>
                                <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
                                    <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Required Attachments</p>
                                    <button type="button" class="app-btn btn-neutral" data-add-required-attachment>Add Name of Required Attachment</button>
                                </div>
                                <div class="grid gap-2" data-required-attachments-list>
                                    <c:forEach items="${requiredAttachmentsByProductId[product.id]}" var="attachmentRequirement">
                                        <div class="grid gap-2 rounded border border-slate-200 bg-slate-50 p-2 sm:grid-cols-[minmax(0,1fr)_8rem_auto]" data-required-attachment-row>
                                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                                Name
                                                <input name="requiredAttachmentName" type="text" maxlength="120" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${attachmentRequirement.attachmentName}" />
                                            </label>
                                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                                Max MB
                                                <input name="requiredAttachmentMaxSizeMb" type="number" min="0.01" max="100" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${attachmentRequirement.maxSizeMb}" />
                                            </label>
                                            <button type="button" class="app-btn btn-neutral self-end" data-remove-required-attachment>Remove</button>
                                        </div>
                                    </c:forEach>
                                </div>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="role.disbursementOfficer" text="Disbursement/Teller Officer" /></p>
                            <div class="workflow-support-grid">
                                <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    <input name="disbursementProofRequired" type="checkbox" value="true" ${product.disbursementProofRequired != false ? 'checked' : ''} />
                                    <span><spring:message code="admin.settings.requireProofBeforeDisbursement" text="Require proof attachment before disbursement" /></span>
                                </label>
                                <input type="hidden" name="disbursementProofRequired" value="false" />
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="admin.settings.workflow.guarantorSettings" text="Guarantor Settings" /></p>
                            <div class="workflow-support-grid">
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    <spring:message code="admin.settings.workflow.guarantorsRequired" text="Guarantors Required" />
                                    <input name="guarantorsRequired" type="number" min="0" max="15" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.guarantorsRequired}" data-number-range-max="15" data-workflow-guarantors />
                                </label>
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    Minimum Guarantor Savings
                                    <input name="guarantorMinimumSavings" type="text" inputmode="decimal" data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.resolvedGuarantorMinimumSavings}" />
                                </label>
                                <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    <input name="guarantorMinSavingsCheckRequired" type="checkbox" value="true" ${product.guarantorMinSavingsCheckRequired ? 'checked' : ''} />
                                    <span><spring:message code="admin.settings.checkGuarantorSavings" text="Check guarantor minimum savings before selection" /></span>
                                </label>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="admin.settings.workflow.validation" text="Validation" /></p>
                            <div class="workflow-warning-stack mt-4" data-workflow-warnings></div>
                        </div>
                    </div>
                </section>

                <section class="product-builder-section">
                    <div class="product-builder-section-header">
                        <p class="erp-widget-title"><spring:message code="admin.settings.workflow.preview" text="Preview" /></p>
                        <h3 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.workflow.resolvedFlow" text="Resolved Flow" /></h3>
                        <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.workflow.resolvedFlowHelp" text="The runtime path below reflects what the system would execute with current tenant settings." /></p>
                    </div>
                    <div class="product-builder-section-body">
                        <div class="workflow-preview-shell">
                            <p class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="admin.settings.workflow.title" text="Approval Workflow" /></p>
                            <div class="mt-3 workflow-preview-runtime" data-workflow-preview-runtime></div>
                        </div>
                    </div>
                </section>

                <div class="app-modal-actions">
                    <button type="button"
                            class="app-btn btn-neutral"
                            data-product-modal-close="product-${product.id}">
                        <spring:message code="common.cancel" text="Cancel" />
                    </button>
                    <button type="submit" class="app-btn btn-primary"><spring:message code="common.save" text="Save" /></button>
                </div>
            </form>
            </div>
        </div>
    </div>
</c:forEach>

<spring:message code="admin.settings.workflow.warning.noReviewStage" text="At least one review or approval stage must be enabled before disbursement." var="warningNoReviewStage" />
<spring:message code="admin.settings.workflow.warning.noLoanOfficers" text="Loan Officer review is enabled for this product, but there are no active Loan Officers assigned yet." var="warningNoLoanOfficers" />
<spring:message code="admin.settings.workflow.warning.managerLoanOfficerSamePriority" text="Manager and Loan Officer cannot share the same priority." var="warningManagerLoanOfficerSamePriority" />
<spring:message code="admin.settings.workflow.warning.duplicatePriorities" text="Each enabled review stage must use a different priority." var="warningDuplicatePriorities" />
<spring:message code="admin.settings.workflow.warning.noBoardReviewers" text="Board Member review is configured, but there are no active board members available for assignment." var="warningNoBoardReviewers" />
<spring:message code="admin.settings.workflow.warning.noChairpersons" text="Chairperson review is configured, but there are no active chairpersons available for assignment." var="warningNoChairpersons" />
<spring:message code="admin.settings.workflow.warning.noCommitteeReviewers" text="Credit Committee review is configured, but there are no active credit committee members available for assignment." var="warningNoCommitteeReviewers" />
<spring:message code="admin.settings.workflow.warning.committeeAccountantSamePriority" text="Committee and Accountant cannot share the same priority slot." var="warningCommitteeAccountantSamePriority" />
<spring:message code="admin.settings.workflow.warning.noAccountants" text="Accountant review is enabled for this product, but there are no active accountants available." var="warningNoAccountants" />
<spring:message code="admin.settings.workflow.warning.noDisbursementOfficers" text="Disbursement/Teller Officer is required, but there are no active Disbursement/Teller Officers assigned yet." var="warningNoDisbursementOfficers" />
<spring:message code="admin.settings.workflow.warning.noDisbursementClaimHolders" text="Disbursement/Teller Officer is optional, but no active staff user has both disbursement claims." var="warningNoDisbursementClaimHolders" />
<spring:message code="admin.settings.workflow.warning.loadedFinancialRequired" text="Members must load current financial data before this product can move into review." var="warningLoadedFinancialRequired" />
<spring:message code="admin.settings.workflow.warning.guarantorPrefix" text="This product waits for" var="warningGuarantorPrefix" />
<spring:message code="admin.settings.workflow.warning.guarantorApproval" text="guarantor approval" var="warningGuarantorApproval" />
<spring:message code="admin.settings.workflow.warning.guarantorApprovals" text="guarantor approvals" var="warningGuarantorApprovals" />
<spring:message code="admin.settings.workflow.warning.guarantorSuffix" text="before the review flow starts." var="warningGuarantorSuffix" />
<spring:message code="admin.settings.workflow.runtime.member" text="Member" var="runtimeMember" />
<spring:message code="admin.settings.workflow.runtime.loadedFinancialData" text="Loaded Financial Data (Savings & Shares)" var="runtimeLoadedFinancialData" />
<spring:message code="admin.settings.workflow.runtime.guarantor" text="Guarantor" var="runtimeGuarantor" />
<spring:message code="admin.settings.workflow.runtime.guarantors" text="Guarantors" var="runtimeGuarantors" />
<spring:message code="admin.settings.workflow.runtime.fixManagerLoanOfficerPriority" text="Fix Priority Slots" var="runtimeFixManagerLoanOfficerPriority" />
<spring:message code="admin.settings.workflow.runtime.disbursementRelease" text="Disbursement Release" var="runtimeDisbursementRelease" />
<spring:message code="role.manager" text="Manager" var="runtimeManager" />
<spring:message code="role.loanOfficer" text="Loan Officer" var="runtimeLoanOfficer" />
<spring:message code="role.chairperson" text="Chairperson" var="runtimeChairperson" />
<spring:message code="role.boardMember" text="Board Member" var="runtimeBoardMember" />
<spring:message code="role.committee" text="Credit Committee" var="runtimeCommittee" />
<spring:message code="role.accountant" text="Accountant" var="runtimeAccountant" />
<spring:message code="role.disbursementOfficer" text="Disbursement/Teller Officer" var="runtimeDisbursementOfficer" />
<spring:message code="admin.settings.validation.displayOrder" text="Display order must be 1 or higher." var="validationDisplayOrder" />
<spring:message code="admin.settings.validation.productNameRequired" text="Enter a loan product name." var="validationProductNameRequired" />
<spring:message code="admin.settings.validation.productNameLength" text="Loan product name must be 120 characters or fewer." var="validationProductNameLength" />
<spring:message code="admin.settings.validation.productDescriptionRequired" text="Enter a product description." var="validationProductDescriptionRequired" />
<spring:message code="admin.settings.validation.descriptionLength" text="Keep the description within 500 characters." var="validationDescriptionLength" />
<spring:message code="admin.settings.validation.minimumAmount" text="Minimum amount cannot be negative." var="validationMinimumAmount" />
<spring:message code="admin.settings.validation.maximumAmountPositive" text="Maximum amount must be greater than zero." var="validationMaximumAmountPositive" />
<spring:message code="admin.settings.validation.maximumBelowMinimum" text="Maximum amount cannot be lower than the minimum amount." var="validationMaximumBelowMinimum" />
<spring:message code="admin.settings.validation.savingsPercent" text="Loan savings multiple must be between 0 and 10x." var="validationSavingsPercent" />
<spring:message code="admin.settings.validation.insurancePercent" text="Insurance percentage cannot be negative." var="validationInsurancePercent" />
<spring:message code="admin.settings.validation.annualInterestPercent" text="Annual interest percentage cannot be negative." var="validationAnnualInterestPercent" />
<spring:message code="admin.settings.validation.minRepaymentMonths" text="Minimum repayment period must be at least 1 month." var="validationMinRepaymentMonths" />
<spring:message code="admin.settings.validation.maxRepaymentMonths" text="Maximum repayment period must be at least 1 month." var="validationMaxRepaymentMonths" />
<spring:message code="admin.settings.validation.maxRepaymentBelowMin" text="Maximum repayment period cannot be lower than the minimum repayment period." var="validationMaxRepaymentBelowMin" />
<spring:message code="admin.settings.validation.guarantorsRequired" text="Guarantors required cannot be negative." var="validationGuarantorsRequired" />
<spring:message code="admin.settings.validation.countRange" text="Enter a number from 0 to 15." var="validationCountRange" />
<spring:message code="admin.settings.validation.committeeCountRange" text="Enter a number from 1 to 15." var="validationCommitteeCountRange" />
<spring:message code="admin.settings.validation.noReviewStage" text="Enable at least one review or approval stage before disbursement." var="validationNoReviewStage" />
<spring:message code="admin.settings.validation.managerPriority" text="Manager priority must be between 1 and 6." var="validationManagerPriority" />
<spring:message code="admin.settings.validation.loanOfficerPriority" text="Loan Officer priority must be between 1 and 6." var="validationLoanOfficerPriority" />
<spring:message code="admin.settings.validation.openPriority" text="Choose an available priority for active review roles." var="validationOpenPriority" />
<spring:message code="admin.settings.validation.managerLoanOfficerDifferentPriority" text="Choose different priorities for Manager and Loan Officer." var="validationManagerLoanOfficerDifferentPriority" />
<spring:message code="admin.settings.validation.assignLoanOfficer" text="Assign at least one active Loan Officer before using this stage." var="validationAssignLoanOfficer" />
<spring:message code="admin.settings.validation.assignChairperson" text="Assign at least one active Chairperson before using this stage." var="validationAssignChairperson" />
<spring:message code="admin.settings.validation.assignBoardReviewer" text="Assign at least one active board member before using this stage." var="validationAssignBoardReviewer" />
<spring:message code="admin.settings.validation.assignCommitteeReviewer" text="Assign at least one active credit committee member before using this stage." var="validationAssignCommitteeReviewer" />
<spring:message code="admin.settings.validation.committeeAccountantDifferentPriority" text="Committee and Accountant cannot share the same priority slot." var="validationCommitteeAccountantDifferentPriority" />
<spring:message code="admin.settings.validation.assignAccountant" text="Assign at least one active Accountant before using this stage." var="validationAssignAccountant" />
<spring:message code="admin.settings.validation.assignDisbursementOfficer" text="Assign at least one active Disbursement/Teller Officer before requiring this role." var="validationAssignDisbursementOfficer" />
<spring:message code="admin.settings.validation.assignDisbursementClaims" text="Grant both disbursement claims to at least one active staff user before removing this role requirement." var="validationAssignDisbursementClaims" />
<spring:message code="admin.settings.validation.applicationFee" text="Application fee cannot be negative." var="validationApplicationFee" />
<spring:message code="admin.settings.validation.processingFeePercent" text="Loan processing fee percentage cannot be negative." var="validationProcessingFeePercent" />
<%@ include file="../fragments/loan-product-workflow-script.jspf" %>

<%@ include file="../fragments/footer.jspf" %>
