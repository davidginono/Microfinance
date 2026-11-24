<%@ page pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/admin-workspace-support.jspf" %>


<c:set var="customLoanConfiguration" value="${settingsSection eq 'loan' and param.custom eq 'true'}" />
<c:set var="creatingLoanProduct" value="${settingsSection eq 'loan' and not customLoanConfiguration and (param.task eq 'create' or openProductModalKey eq 'create-product')}" />
<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.settings.breadcrumb" text="Admin Tools / Settings & Controls" /><c:if test="${creatingLoanProduct}"> / <spring:message code="admin.settings.createLoanProduct" /></c:if></p>
    <h1 class="erp-page-title"><c:choose><c:when test="${creatingLoanProduct}"><spring:message code="admin.settings.createLoanProduct" /></c:when><c:when test="${customLoanConfiguration}"><spring:message code="menu.admin.settings.customisedLoan" /></c:when><c:otherwise><spring:message code="admin.settings.title" /></c:otherwise></c:choose></h1>
</div>

<c:if test="${not empty openProductModalKey and not creatingLoanProduct}">
    <div hidden data-open-product-modal="${fn:escapeXml(openProductModalKey)}"></div>
</c:if>

<c:if test="${settingsSection eq 'otp'}">
    <section class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
            <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="admin.settings.otp.title" text="Branch OTP Delivery" /></h2>
        </div>
        <form action="/admin/settings-controls/otp-delivery" method="post" class="erp-panel-body">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div class="grid gap-3 lg:grid-cols-3">
                <label class="settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                    <input name="otpDeliveryChannel" type="radio" value="EMAIL" class="mt-1" ${stationOtpDeliveryChannel eq 'EMAIL' ? 'checked' : ''} />
                    <span>
                        <span class="block font-semibold text-slate-900"><spring:message code="admin.settings.otp.email" text="Email" /></span>
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

</c:if>

<c:if test="${settingsSection eq 'guarantor'}">
    <section class="erp-panel aws-settings-panel aws-qualification-settings mb-4" aria-labelledby="qualification-settings-title">
        <div class="aws-settings-header">
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
    <sec:authorize access="@access.hasAny(principal,'LOAN_PRODUCTS_VIEW','APPROVAL_FLOW_VIEW')">
    <c:if test="${customLoanConfiguration}">
    <sec:authorize access="@access.has(principal,'APPROVAL_FLOW_VIEW')">
      <section class="erp-panel">
        <div class="erp-panel-header"><h2><spring:message code="admin.settings.workflow.title" /></h2></div>
        <form method="post" action="<c:url value='/admin/settings-controls/custom-loan/approval' />" class="erp-panel-body">
          <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
          <%@ include file="../fragments/loan-product-approval-fields.jspf" %>
          <sec:authorize access="@access.has(principal,'APPROVAL_FLOW_CONFIGURE')"><div class="aws-settings-footer"><button class="app-btn btn-launch"><spring:message code="common.save" /></button></div></sec:authorize>
        </form>
      </section>
    </sec:authorize>
    </c:if>

    <c:if test="${not creatingLoanProduct and not customLoanConfiguration}">
    <section class="erp-table-wrap" data-resource-table="admin-loan-products" data-resource-split>
      <div class="app-table-titlebar"><h2><spring:message code="admin.workspace.loanProducts" /> <span class="app-table-count">(${fn:length(products)})</span></h2><div class="app-table-toolbar">
      <sec:authorize access="@access.has(principal,'LOAN_PRODUCTS_CREATE')"><a class="app-btn btn-launch" href="<c:url value='/admin/settings-controls?section=loan&amp;task=create' />"><spring:message code="admin.settings.loanProducts.add" /></a></sec:authorize>
      </div></div>
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
    </sec:authorize>
</c:if>

<c:if test="${settingsSection eq 'loan' and not customLoanConfiguration}">
    <div class="app-modal-overlay hidden"
         data-product-modal="application-fee">
        <div class="app-modal-panel max-w-2xl">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
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
<div class="product-config-workspace" data-product-config-workspace data-resource-form-page>
    <section class="product-config-workspace-nav">
        <%@ include file="../fragments/loan-product-config-tabs.jspf" %>
    </section>
    <div class="product-config-workspace-main">
                <form id="loanProductCreateForm" action="/admin/settings-controls/loan-products"
                      method="post"
                      class="product-builder-form accounting-create-form admin-product-create"
                      data-inline-validation-form="loan-settings"
                      data-product-workflow-builder="true"
                      data-product-config-form
                      data-tenant-loan-officer-enabled="${settings.loanOfficerReviewRequired}"
                      data-tenant-board-enabled="${settings.boardReviewRequired}">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="modalKey" value="create-product" />
                    <input type="hidden" name="productCode" value="" data-product-code-field data-product-code-generated="true" />

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

                    <%@ include file="../fragments/loan-product-config-terms.jspf" %>
                    <section class="product-builder-section product-config-step" data-product-config-step="workflow">
                        <div class="product-builder-section-header"><h2><spring:message code="admin.settings.workflow.title" /></h2></div>
                        <div class="product-builder-section-body" data-product-config-panel hidden>
                            <c:set var="guarantorProduct" value="${null}" /><c:set var="guarantorFormId" value="loanProductCreateForm" />
                            <%@ include file="../fragments/loan-product-guarantor-field.jspf" %>
                            <sec:authorize access="@access.has(principal,'APPROVAL_FLOW_VIEW')"><%@ include file="../fragments/loan-product-approval-fields.jspf" %></sec:authorize>
                        </div>
                    </section>
                    <%@ include file="../fragments/loan-product-config-accounts-documents.jspf" %>
                    <div class="product-config-actionbar"><div class="product-config-save-actions">
                        <a class="app-btn btn-neutral" href="<c:url value='/admin/settings-controls?section=loan' />"><spring:message code="common.cancel" /></a>
                        <button type="submit" class="app-btn btn-launch"><spring:message code="admin.settings.loanProducts.add" text="Add loan product" /></button>
                    </div></div>
                </form>
    </div>
</div>
<%@ include file="../accounting/accounting-form-support.jspf" %>
</c:if>


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


<link rel="stylesheet" href="<c:url value='/css/loan-tenure-config.css' />" /><script src="<c:url value='/js/loan-tenure-config.js' />" defer></script><script src="<c:url value='/js/loan-recording.js' />" defer></script><%@ include file="../fragments/loan-product-editor-script.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
