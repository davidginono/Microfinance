<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/admin-workspace-support.jspf" %>


<c:set var="creatingLoanProduct" value="${settingsSection eq 'loan' and (param.task eq 'create' or openProductModalKey eq 'create-product')}" />
<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.settings.breadcrumb" text="Admin Tools / Settings & Controls" /><c:if test="${creatingLoanProduct}"> / <spring:message code="admin.settings.createLoanProduct" /></c:if></p>
    <h1 class="erp-page-title"><c:choose><c:when test="${creatingLoanProduct}"><spring:message code="admin.settings.createLoanProduct" /></c:when><c:otherwise><spring:message code="admin.settings.title" /></c:otherwise></c:choose></h1>
</div>

<c:if test="${not empty openProductModalKey and not creatingLoanProduct}">
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
                <button type="submit" class="app-btn btn-launch"><spring:message code="admin.settings.language.save" text="Save Language" /></button>
            </div>
        </form>
    </section>
</c:if>

<c:if test="${settingsSection eq 'otp'}">
    <section class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
            <p class="erp-widget-title"><spring:message code="admin.settings.otp.eyebrow" text="Authentication" /></p>
            <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="admin.settings.otp.title" text="Branch OTP Delivery" /></h2>
            <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.otp.subtitle" text="Choose how this Branch sends authentication and workflow confirmation codes." /></p>
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
                        <span class="mt-1 block text-xs text-slate-500"><spring:message code="admin.settings.otp.smsOnlyHelp" text="Use normal Branch SMS units. OTP requests stop when units are depleted." /></span>
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
            <div class="mt-5 border-t border-slate-200 pt-5">
                <h3 class="aws-settings-title">User OTP obligation</h3>
                <p class="aws-settings-description">Select a minimum, or clear the selected option to set no minimum.</p>
                <div class="mt-3 grid gap-3 md:grid-cols-2">
                    <label class="settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                        <input name="otpSelectionPolicy" type="radio" value="AT_LEAST_ONE" class="mt-1"
                               data-clearable-otp-policy
                               ${stationOtpSelectionPolicy eq 'AT_LEAST_ONE' ? 'checked' : ''} />
                        <span>
                            <span class="block font-semibold text-slate-900">At least one</span>
                            <span class="mt-1 block text-xs text-slate-500">Users select login OTP, approval OTP, or both.</span>
                        </span>
                    </label>
                    <label class="settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                        <input name="otpSelectionPolicy" type="radio" value="BOTH" class="mt-1"
                               data-clearable-otp-policy
                               ${stationOtpSelectionPolicy eq 'BOTH' ? 'checked' : ''} />
                        <span>
                            <span class="block font-semibold text-slate-900">Both required</span>
                            <span class="mt-1 block text-xs text-slate-500">All users must use OTP for login and approvals.</span>
                        </span>
                    </label>
                </div>
            </div>
            <div class="relative mt-5 flex flex-row items-center justify-between gap-2 flex-wrap">
                <p class="text-sm text-slate-500"><spring:message code="admin.settings.otp.reserveNotice" text="The three reserved SMS alert units are never used for OTP codes." /></p>
                <button type="submit" class="app-btn btn-launch"><spring:message code="admin.settings.otp.save" text="Save OTP Settings" /></button>
            </div>
        </form>
    </section>
    <script>
        (() => {
            const policies = document.querySelectorAll("[data-clearable-otp-policy]");
            policies.forEach((policy) => {
                let clearOnClick = false;
                const rememberSelection = () => {
                    clearOnClick = policy.checked;
                };
                policy.addEventListener("pointerdown", rememberSelection);
                policy.addEventListener("keydown", (event) => {
                    if (event.key === " " || event.key === "Enter") {
                        rememberSelection();
                    }
                });
                policy.addEventListener("click", () => {
                    if (clearOnClick) {
                        policy.checked = false;
                    }
                    clearOnClick = false;
                });
            });
        })();
    </script>
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
                    Active Committee Members: <strong>${activeBoardMemberCount}</strong>
                </div>
                <div class="border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
                    Active Accountants: <strong>${activeAccountantCount}</strong>
                </div>
                <div class="border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
                    Active Disbursement Claim Holders: <strong>${activeDisbursementClaimHolderCount}</strong>
                </div>
            </div>

            <div class="aws-settings-footer">
                <button type="submit" class="app-btn btn-launch"><spring:message code="common.save" text="Save" /></button>
            </div>
        </form>
    </div>
</c:if>

<c:if test="${settingsSection eq 'guarantor'}">
    <section class="erp-panel aws-settings-panel aws-qualification-settings mb-4" aria-labelledby="qualification-settings-title">
        <div class="aws-settings-header">
            <p class="aws-settings-kicker"><spring:message code="admin.settings.qualification.eyebrow" text="Qualification Policies" /></p>
            <h2 id="qualification-settings-title" class="aws-settings-title"><spring:message code="admin.settings.qualification.title" text="Qualification Controls" /></h2>
        </div>
        <form action="/admin/settings-controls/qualification-policies" method="post" class="aws-settings-form aws-qualification-form">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div class="aws-settings-grid aws-qualification-grid">
                <section class="aws-settings-group aws-qualification-applicants-group" aria-labelledby="loan-applicant-policy-title">
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
                <section class="aws-settings-group aws-qualification-guarantors-group" aria-labelledby="guarantor-policy-title">
                    <div class="aws-settings-group-header">
                        <h3 id="guarantor-policy-title"><spring:message code="loan.guarantors" text="Guarantors" /></h3>
                    </div>
                    <div class="aws-settings-group-body aws-settings-policy-grid">
                        <label class="aws-settings-checkbox-row">
                            <input class="aws-settings-checkbox" name="guarantorWithActiveLoanAllowed" type="checkbox" value="true" ${policyGuarantorWithActiveLoanAllowed ? 'checked' : ''} />
                            <span class="aws-settings-checkbox-copy">
                                <span class="aws-settings-checkbox-title"><spring:message code="admin.settings.allowActiveLoanGuarantors" text="Allow guarantors with active loans" /></span>
                                <span class="aws-settings-help"><spring:message code="admin.settings.allowActiveLoanGuarantorsHelp" text="If unchecked, clients with active loans cannot guarantee." /></span>
                            </span>
                        </label>
                        <fmt:formatNumber value="${policyGuarantorMaxGuaranteedLoanAmount}" maxFractionDigits="0" groupingUsed="false" var="policyGuarantorMaxGuarantees" />
                        <label class="aws-settings-field aws-settings-field--compact">
                            Max Guarantees
                            <input name="guarantorMaxGuaranteedLoanAmount" type="number" data-amount-input="true" min="0" max="15" step="1" data-number-range-max="15" class="aws-control" value="${policyGuarantorMaxGuarantees}" />
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
                <section class="aws-settings-group aws-qualification-risk-group" aria-labelledby="portfolio-risk-policy-title">
                    <div class="aws-settings-group-header">
                        <h3 id="portfolio-risk-policy-title"><spring:message code="admin.settings.portfolioRisk" text="Portfolio Risk" /></h3>
                    </div>
                    <div class="aws-settings-group-body aws-settings-risk-body">
                        <label class="aws-settings-field aws-settings-field--compact">
                            <spring:message code="admin.settings.portfolioAtRiskDays" text="Portfolio At Risk Days" />
                            <input name="portfolioAtRiskDays" type="number" min="1" max="365" step="1" data-number-range-max="365" class="aws-control" value="${policyPortfolioAtRiskDays}" />
                        </label>
                    </div>
                </section>
            </div>
            <div class="aws-settings-footer">
                <button type="submit" class="app-btn btn-launch aws-settings-save-action"><spring:message code="admin.settings.saveConfiguration" text="Save configuration" /></button>
            </div>
        </form>
    </section>

</c:if>

<c:if test="${settingsSection eq 'loan'}">
    <c:if test="${customisedWorkflowSaved}"><p role="status"><spring:message code="customised.workflow.saved" /></p></c:if>

    <c:if test="${not creatingLoanProduct}">
    <section class="erp-table-wrap" data-resource-table="admin-loan-products" data-resource-split>
      <div class="app-table-titlebar"><h2><spring:message code="admin.workspace.loanProducts" /> <span class="app-table-count">(${fn:length(products)})</span></h2><div class="app-table-toolbar"><a class="app-btn btn-launch" href="<c:url value='/admin/settings-controls?section=loan&amp;task=create' />"><spring:message code="admin.settings.loanProducts.add" /></a></div></div>
      <div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="local"><thead><tr>
      <th data-table-order="name"><spring:message code="admin.settings.loanProducts.name" /></th><th data-table-order="status"><spring:message code="common.status" /></th><th data-table-order="minimum"><spring:message code="admin.settings.loanProducts.minimumAmount" /> (TZS)</th><th data-table-order="maximum"><spring:message code="admin.settings.loanProducts.maximumAmount" /> (TZS)</th><th data-table-order="interest"><spring:message code="admin.settings.loanProducts.annualInterestPercent" /></th><th data-table-order="method"><spring:message code="admin.settings.loanProducts.amountReturnMethod" /></th><th data-table-actions-header><spring:message code="common.actions" /></th></tr></thead><tbody>
      <c:forEach items="${products}" var="product"><tr id="loan-product-${product.id}" data-created-loan-product-target="${createdLoanProductId eq product.id}">
      <td><a class="resource-table-id-link" href="<c:url value='/admin/settings-controls/loan-products/${product.id}/edit' />"><c:out value="${product.displayName}" /></a></td>
      <td><span data-resource-status="${product.status}"><spring:message code="status.${fn:toLowerCase(product.status)}" text="${product.status}" /></span></td>
      <td data-sort-value="${product.minimumAmount}"><fmt:formatNumber value="${product.minimumAmount}" minFractionDigits="2" maxFractionDigits="2" /></td>
      <td data-sort-value="${product.maximumAmount}"><c:choose><c:when test="${product.maximumAmount ne null}"><fmt:formatNumber value="${product.maximumAmount}" minFractionDigits="2" maxFractionDigits="2" /></c:when><c:otherwise><spring:message code="common.notSet" /></c:otherwise></c:choose></td>
      <td data-sort-value="${product.interestRate}"><c:choose><c:when test="${product.interestRate ne null}"><fmt:formatNumber value="${product.interestRate * 100}" maxFractionDigits="4" />%</c:when><c:otherwise>—</c:otherwise></c:choose></td>
      <td><c:choose><c:when test="${product.interestMethod eq 'FLAT_RATE'}"><spring:message code="admin.settings.loanProducts.flatRate" /></c:when><c:when test="${product.interestMethod eq 'REDUCING_BALANCE'}"><spring:message code="admin.settings.loanProducts.reducingBalance" /></c:when><c:otherwise>—</c:otherwise></c:choose></td>
      <td><a class="app-btn btn-neutral" href="<c:url value='/admin/settings-controls/loan-products/${product.id}/edit' />"><spring:message code="admin.workspace.configureProduct" /></a></td></tr></c:forEach>
      <c:if test="${empty products}"><tr><td colspan="7"><spring:message code="admin.workspace.noProducts" /></td></tr></c:if>
      </tbody></table></div></section></c:if>
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
                        <button type="submit" class="app-btn btn-launch"><spring:message code="common.save" text="Save" /></button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</c:if>

<c:if test="${creatingLoanProduct}">
<div class="admin-focused-form">
                <form action="/admin/settings-controls/loan-products"
                      method="post"
                      class="product-builder-form accounting-create-form admin-product-create"
                      data-inline-validation-form="loan-settings"
                      data-product-workflow-builder="true"
                      data-tenant-loan-officer-enabled="${settings.loanOfficerReviewRequired}"
                      data-tenant-board-enabled="${settings.boardReviewRequired}">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="modalKey" value="create-product" />
                    <input type="hidden" name="productCode" value="" data-product-code-field data-product-code-generated="true" />
                    <input type="hidden" name="workflowStartStage" value="MANAGER" data-workflow-start-stage-field />
                    <c:if test="${openProductModalKey eq 'create-product' and not empty loanProductFormDraft}">
                        <div hidden data-loan-product-form-draft>
                            <c:forEach items="${loanProductFormDraft}" var="draftField">
                                <c:forEach items="${draftField.value}" var="draftValue">
                                    <span data-loan-product-form-value="${fn:escapeXml(draftField.key)}"><c:out value="${draftValue}" /></span>
                                </c:forEach>
                            </c:forEach>
                        </div>
                    </c:if>

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
                            <h2 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.loanProducts.identity" text="Product Identity" /></h2>
                        </div>
                        <div class="product-builder-section-body product-identity-grid">
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
                                <textarea name="productDescription" rows="3" required maxlength="500" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder='<spring:message code="admin.settings.loanProducts.descriptionPlaceholder" text="Short description shown to clients when choosing this product." />'></textarea>
                            </label>
                        </div>
                    </section>

                    <section class="product-builder-section">
                        <div class="product-builder-section-header">
                            <p class="erp-widget-title"><spring:message code="admin.settings.loanProducts.eligibility" text="Eligibility" /></p>
                            <h2 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.loanProducts.eligibility" text="Eligibility" /></h2>
                            <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.eligibilityCreateHelp" text="Define lending limits and Disposable Income coverage for this product." /></p>
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

                            <c:set var="eligibilityProduct" value="${null}" />
                            <%@ include file="../fragments/loan-product-eligibility-fields.jspf" %>
                            <c:set var="tenureProduct" value="${null}" /><%@ include file="../fragments/loan-tenure-fields.jspf" %>
                        </div>
                    </section>

                    <section class="product-builder-section">
                        <div class="product-builder-section-header">
                            <p class="erp-widget-title"><spring:message code="admin.settings.loanProducts.repayment" text="Repayment" /></p>
                            <h2 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.loanProducts.repaymentCharges" text="Repayment & Charges" /></h2>
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
                            <h2 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.workflow.title" text="Approval Workflow" /></h2>
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
                                        <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.boardMember" text="Committee Member" /></div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                            <label class="workflow-checkbox-lock"><input name="boardReviewRequired" type="checkbox" value="true" data-workflow-board aria-label="Committee Member required" /></label>
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
                                <p class="workflow-subsection-title"><spring:message code="role.boardMember" text="Committee Member" /></p>
                                <div class="grid gap-3">
                                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                        Committee Members Assigned
                                        <input type="search" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="Search Committee Members..." data-board-reviewer-search />
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
                                            <div class="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700">No active Committee Members are available.</div>
                                        </c:if>
                                    </div>
                                    <p class="text-xs text-slate-500">Every selected Committee Member will be assigned and notified for this product.</p>
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
                                            <div class="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700">No active credit committee Members are available.</div>
                                        </c:if>
                                    </div>
                                    <p class="text-xs text-slate-500">Every selected credit committee Member will be assigned and notified for this product.</p>
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
                                        Minimum Guarantor Disposable Income
                                        <input name="guarantorMinimumSavings" type="text" inputmode="decimal" data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="0" />
                                    </label>
                                    <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                        <input name="guarantorMinSavingsCheckRequired" type="checkbox" value="true" />
                                        <span><spring:message code="admin.settings.checkGuarantorSavings" text="Check guarantor minimum Disposable Income before selection" /></span>
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
                            <h2 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.workflow.resolvedFlow" text="Resolved Flow" /></h2>
                            <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.workflow.resolvedFlowHelp" text="The runtime path below reflects what the system would execute with current tenant settings." /></p>
                        </div>
                        <div class="product-builder-section-body">
                            <div class="workflow-preview-shell">
                                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="admin.settings.workflow.title" text="Approval Workflow" /></p>
                                <div class="mt-3 workflow-preview-runtime" data-workflow-preview-runtime></div>
                            </div>
                        </div>
                    </section>

                    <div class="admin-form-actions">
                        <a class="app-btn btn-neutral" href="<c:url value='/admin/settings-controls?section=loan' />"><spring:message code="common.cancel" /></a>
                        <button type="submit" class="app-btn btn-launch"><spring:message code="admin.settings.loanProducts.add" text="Add loan product" /></button>
                    </div>
                </form>
</div>
<%@ include file="../accounting/accounting-form-support.jspf" %>
</c:if>


<spring:message code="admin.settings.workflow.warning.noReviewStage" text="At least one review or approval stage must be enabled before disbursement." var="warningNoReviewStage" />
<spring:message code="admin.settings.workflow.warning.noLoanOfficers" text="Loan Officer review is enabled for this product, but there are no active Loan Officers assigned yet." var="warningNoLoanOfficers" />
<spring:message code="admin.settings.workflow.warning.managerLoanOfficerSamePriority" text="Manager and Loan Officer cannot share the same priority." var="warningManagerLoanOfficerSamePriority" />
<spring:message code="admin.settings.workflow.warning.duplicatePriorities" text="Each enabled review stage must use a different priority." var="warningDuplicatePriorities" />
<spring:message code="admin.settings.workflow.warning.noBoardReviewers" text="Committee Member review is configured, but there are no active Committee Members available for assignment." var="warningNoBoardReviewers" />
<spring:message code="admin.settings.workflow.warning.noChairpersons" text="Chairperson review is configured, but there are no active chairpersons available for assignment." var="warningNoChairpersons" />
<spring:message code="admin.settings.workflow.warning.noCommitteeReviewers" text="Credit Committee review is configured, but there are no active credit committee Members available for assignment." var="warningNoCommitteeReviewers" />
<spring:message code="admin.settings.workflow.warning.committeeAccountantSamePriority" text="Committee and Accountant cannot share the same priority slot." var="warningCommitteeAccountantSamePriority" />
<spring:message code="admin.settings.workflow.warning.noAccountants" text="Accountant review is enabled for this product, but there are no active accountants available." var="warningNoAccountants" />
<spring:message code="admin.settings.workflow.warning.noDisbursementOfficers" text="Disbursement/Teller Officer is required, but there are no active Disbursement/Teller Officers assigned yet." var="warningNoDisbursementOfficers" />
<spring:message code="admin.settings.workflow.warning.noDisbursementClaimHolders" text="Disbursement/Teller Officer is optional, but no active staff user has both disbursement claims." var="warningNoDisbursementClaimHolders" />
<spring:message code="admin.settings.workflow.warning.loadedFinancialRequired" text="Clients must provide current assessment data before this product can move into review." var="warningLoadedFinancialRequired" />
<spring:message code="admin.settings.workflow.warning.guarantorPrefix" text="This product waits for" var="warningGuarantorPrefix" />
<spring:message code="admin.settings.workflow.warning.guarantorApproval" text="guarantor approval" var="warningGuarantorApproval" />
<spring:message code="admin.settings.workflow.warning.guarantorApprovals" text="guarantor approvals" var="warningGuarantorApprovals" />
<spring:message code="admin.settings.workflow.warning.guarantorSuffix" text="before the review flow starts." var="warningGuarantorSuffix" />
<spring:message code="admin.settings.workflow.runtime.member" text="Client" var="runtimeMember" />
<spring:message code="admin.settings.workflow.runtime.loadedFinancialData" text="Loaded Financial Data (Credit Assessment Data)" var="runtimeLoadedFinancialData" />
<spring:message code="admin.settings.workflow.runtime.guarantor" text="Guarantor" var="runtimeGuarantor" />
<spring:message code="admin.settings.workflow.runtime.guarantors" text="Guarantors" var="runtimeGuarantors" />
<spring:message code="admin.settings.workflow.runtime.fixManagerLoanOfficerPriority" text="Fix Priority Slots" var="runtimeFixManagerLoanOfficerPriority" />
<spring:message code="admin.settings.workflow.runtime.disbursementRelease" text="Disbursement Release" var="runtimeDisbursementRelease" />
<spring:message code="role.manager" text="Manager" var="runtimeManager" />
<spring:message code="role.loanOfficer" text="Loan Officer" var="runtimeLoanOfficer" />
<spring:message code="role.chairperson" text="Chairperson" var="runtimeChairperson" />
<spring:message code="role.boardMember" text="Committee Member" var="runtimeBoardMember" />
<spring:message code="role.committee" text="Credit Committee" var="runtimeCommittee" />
<spring:message code="role.accountant" text="Accountant" var="runtimeAccountant" />
<spring:message code="role.disbursementOfficer" text="Disbursement/Teller Officer" var="runtimeDisbursementOfficer" />
<spring:message code="admin.settings.validation.productNameRequired" text="Enter a loan product name." var="validationProductNameRequired" />
<spring:message code="admin.settings.validation.productNameLength" text="Loan product name must be 120 characters or fewer." var="validationProductNameLength" />
<spring:message code="admin.settings.validation.productDescriptionRequired" text="Enter a product description." var="validationProductDescriptionRequired" />
<spring:message code="admin.settings.validation.descriptionLength" text="Keep the description within 500 characters." var="validationDescriptionLength" />
<spring:message code="admin.settings.validation.minimumAmount" text="Minimum amount cannot be negative." var="validationMinimumAmount" />
<spring:message code="admin.settings.validation.maximumAmountPositive" text="Maximum amount must be greater than zero." var="validationMaximumAmountPositive" />
<spring:message code="admin.settings.validation.maximumBelowMinimum" text="Maximum amount cannot be lower than the minimum amount." var="validationMaximumBelowMinimum" />
<spring:message code="admin.settings.validation.savingsPercent" text="Loan Disposable Income multiple must be between 0 and 10x." var="validationsavingsPercent" />
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
<spring:message code="admin.settings.validation.assignBoardReviewer" text="Assign at least one active Committee Member before using this stage." var="validationAssignBoardReviewer" />
<spring:message code="admin.settings.validation.assignCommitteeReviewer" text="Assign at least one active credit committee Member before using this stage." var="validationAssignCommitteeReviewer" />
<spring:message code="admin.settings.validation.committeeAccountantDifferentPriority" text="Committee and Accountant cannot share the same priority slot." var="validationCommitteeAccountantDifferentPriority" />
<spring:message code="admin.settings.validation.assignAccountant" text="Assign at least one active Accountant before using this stage." var="validationAssignAccountant" />
<spring:message code="admin.settings.validation.assignDisbursementOfficer" text="Assign at least one active Disbursement/Teller Officer before requiring this role." var="validationAssignDisbursementOfficer" />
<spring:message code="admin.settings.validation.assignDisbursementClaims" text="Grant both disbursement claims to at least one active staff user before removing this role requirement." var="validationAssignDisbursementClaims" />
<spring:message code="admin.settings.validation.applicationFee" text="Application fee cannot be negative." var="validationApplicationFee" />
<spring:message code="admin.settings.validation.processingFeePercent" text="Loan processing fee percentage cannot be negative." var="validationProcessingFeePercent" />
<%@ include file="../fragments/loan-product-workflow-script.jspf" %>

<link rel="stylesheet" href="<c:url value='/css/loan-tenure-config.css' />" /><script src="<c:url value='/js/loan-tenure-config.js' />" defer></script><script src="<c:url value='/js/loan-recording.js' />" defer></script><%@ include file="../fragments/footer.jspf" %>
