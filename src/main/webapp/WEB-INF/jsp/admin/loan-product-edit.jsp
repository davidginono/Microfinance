<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/admin-workspace-support.jspf" %>


<c:if test="${chargePolicySaved}"><spring:message code="charges.policySaved" var="chargeSavedMessage" /><div hidden data-toast-message="${fn:escapeXml(chargeSavedMessage)}" data-toast-type="success" data-toast-placement="workspace"></div></c:if>
<div class="erp-page-header" data-aws-page-header>
    <div class="admin-page-heading">
    <p class="erp-breadcrumb"><spring:message code="admin.workspace.productBreadcrumb" /></p>
    <h1 class="erp-page-title" id="admin-loan-product-edit-title"><spring:message code="admin.workspace.configureProduct" /></h1>
    </div>

<div class="product-config-page-actions">
    <a href="<c:url value='/admin/settings-controls'><c:param name='section' value='loan' /></c:url>" class="app-btn btn-neutral"><spring:message code="admin.workspace.backProducts" /></a>
    <a class="app-btn btn-neutral" href="<c:url value='/admin/settings-controls/loan-products/${product.id}/prepayment' />"><spring:message code="prepayment.title" /></a>
    <form action="/admin/settings-controls/loan-products/${product.id}/delete"
          method="post"
          data-confirm-eyebrow="Confirm Deletion"
          data-confirm-title="Delete Loan Product"
          data-confirm-message="Delete this loan product from active settings? Historical loan applications and reports will remain preserved."
          data-confirm-text="Delete product"
          data-confirm-proceed="Delete Product">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <button type="submit" class="app-btn btn-reject">Delete Product</button>
    </form>
</div>
</div>

<c:if test="${chargePolicySaved}"><spring:message code="charges.policySaved" var="chargeSavedMessage" /><div hidden data-toast-message="${fn:escapeXml(chargeSavedMessage)}" data-toast-type="success" data-toast-placement="workspace"></div></c:if>
<div class="product-config-workspace"
     data-product-config-workspace
     data-product-config-stage="Step 2 of 2 &middot; focused product"
     data-product-config-storage-key="loan-product-config-${product.id}">
    <section class="product-config-workspace-nav" aria-label="<spring:message code='admin.workspace.productSections' />">
        <div class="product-config-workspace-summary" >
            <p class="erp-widget-title">Selected product</p>
            <h2><spring:message code="resource.details.summary" /></h2><dl class="erp-detail-facts"><div><dt><spring:message code="admin.settings.loanProducts.name" /></dt><dd><c:out value="${product.displayName}" /></dd></div><div><dt><spring:message code="common.description" /></dt><dd><c:out value="${product.displayDescription}" /></dd></div></dl>
            <div class="product-config-workspace-meta">
                <span class="product-config-status
                    ${product.status eq 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : ''}
                    ${product.status eq 'DRAFT' ? 'bg-amber-50 text-amber-700' : ''}
                    ${product.status eq 'SUSPENDED' ? 'bg-slate-100 text-slate-600' : ''}
                    ${product.status eq 'RETIRED' ? 'bg-rose-50 text-rose-700' : ''}"><span data-resource-status="${product.status}">${product.status}</span></span>
            </div>
        </div>
        <nav class="product-config-step-nav" data-product-config-tabs role="tablist" aria-label="<spring:message code='admin.workspace.productSections' />">
            <button type="button" data-product-config-nav="identity" aria-current="step">
                <span><strong><spring:message code="admin.settings.loanProducts.identity" /></strong></span>
            </button>
            <button type="button" data-product-config-nav="eligibility">
                <span><strong><spring:message code="admin.settings.loanProducts.eligibility" /></strong></span>
            </button>
            <button type="button" data-product-config-nav="pricing">
                <span><strong><spring:message code="admin.settings.loanProducts.repaymentCharges" /></strong></span>
            </button>
            <sec:authorize access="@access.has(principal,'APPROVAL_FLOW_VIEW')"><button type="button" data-product-config-nav="workflow"><span><strong><spring:message code="admin.settings.workflow.title" /></strong></span></button></sec:authorize>
            <button type="button" data-product-config-nav="accounts">
                <span><strong><spring:message code="productAccounts.title" /></strong></span>
            </button>
            <button type="button" data-product-config-nav="evidence">
                <span><strong><spring:message code="recording.attachments" /></strong></span>
            </button>

        </nav>
    </section>

    <div class="product-config-workspace-main">
        <form action="/admin/settings-controls/${product.id}"
                   method="post"
                   class="product-builder-form accounting-create-form"
                  data-inline-validation-form="loan-settings"
                  data-product-workflow-builder="true"
                  data-product-edit-mode="true"
                  data-workflow-notifications="focused"
                  data-tenant-loan-officer-enabled="${settings.loanOfficerReviewRequired}"
                  data-tenant-board-enabled="${settings.boardReviewRequired}"
                  aria-label="Configure ${fn:escapeXml(product.displayName)}"
                  data-product-config-form>
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <input type="hidden" name="modalKey" value="product-${product.id}" />
                <input type="hidden" name="productCode" value="${fn:escapeXml(product.displayCode)}" data-product-code-field />

                <c:if test="${not empty loanProductFormDraft}">
                    <div hidden data-loan-product-form-draft>
                        <c:forEach items="${loanProductFormDraft}" var="draftField">
                            <c:forEach items="${draftField.value}" var="draftValue">
                                <span data-loan-product-form-value="${fn:escapeXml(draftField.key)}"><c:out value="${draftValue}" /></span>
                            </c:forEach>
                        </c:forEach>
                    </div>
                </c:if>
                <c:if test="${not empty message}">
                    <span hidden data-toast-message="${fn:escapeXml(message)}" data-toast-type="success"></span>
                </c:if>
                <c:if test="${not empty error}">
                    <span hidden data-toast-message="${fn:escapeXml(error)}" data-toast-type="error"></span>
                </c:if>
                <c:if test="${not empty loanSettingsFieldErrors}">
                    <div hidden data-modal-server-errors>
                        <c:forEach items="${loanSettingsFieldErrors}" var="fieldError">
                            <div data-modal-field-error="${fieldError.key}"><c:out value="${fieldError.value}" /></div>
                        </c:forEach>
                    </div>
                </c:if>

                <section class="product-builder-section product-config-step is-active" data-product-config-step="identity" aria-label="Product identity"><div class="product-builder-section-header"><h2><spring:message code="admin.settings.loanProducts.identity" /></h2></div>
                    <div id="product-config-identity" class="product-builder-section-body product-identity-grid" data-product-config-panel>
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
                            <textarea name="productDescription" rows="3" required maxlength="500" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder='<spring:message code="admin.settings.loanProducts.descriptionPlaceholder" text="Short description shown to clients when choosing this product." />'>${fn:escapeXml(product.productDescription)}</textarea>
                        </label>
                    </div>
                </section>

                <section class="product-builder-section product-config-step" data-product-config-step="eligibility" aria-label="Eligibility"><div class="product-builder-section-header"><h2><spring:message code="admin.settings.loanProducts.eligibility" /></h2></div>
                    <div id="product-config-eligibility" class="product-builder-section-body product-builder-grid two-up" data-product-config-panel hidden>
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.minimumAmount" text="Minimum Loan Amount" />
                            <input name="minimumAmount" type="text" inputmode="decimal" required data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.minimumAmount}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.maximumAmount" text="Maximum Loan Amount" />
                            <input name="maximumAmount" type="text" inputmode="decimal" data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maximumAmount}" placeholder='<spring:message code="admin.settings.loanProducts.maximumAmountPlaceholder" text="Leave blank if there is no maximum amount" />' />
                        </label>

                        <c:set var="tenureProduct" value="${product}" /><%@ include file="../fragments/loan-tenure-fields.jspf" %>

                        <c:set var="eligibilityProduct" value="${product}" />
                        <%@ include file="../fragments/loan-product-eligibility-fields.jspf" %>
                    </div>
                </section>

                <section class="product-builder-section product-config-step" data-product-config-step="pricing" aria-label="Repayment and charges"><div class="product-builder-section-header"><h2><spring:message code="admin.settings.loanProducts.repaymentCharges" /></h2></div>
                    <div id="product-config-pricing" class="product-builder-section-body product-builder-grid two-up" data-product-config-panel hidden>
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

                <section class="product-builder-section product-config-step" data-product-config-step="accounts" aria-label="<spring:message code='productAccounts.title' />">
                    <sec:authorize access="@access.has(principal,'LOAN_PRODUCTS_CONFIGURE') and @access.has(principal,'ACCOUNTING_ACCOUNTS_UPDATE')" var="canSetChargeAccounts" />
                    <div class="product-builder-section-header">
                        <h2><spring:message code="productAccounts.title" /></h2>
                    </div>
                    <div id="product-config-accounts" class="product-builder-section-body product-builder-grid two-up" data-product-config-panel hidden>
                        <c:set var="productAccountSection" value="${true}" /><c:set var="accountProduct" value="${product}" /><%@ include file="../fragments/loan-product-account-fields.jspf" %><c:set var="productAccountSection" value="${false}" />
                        <c:if test="${canSetChargeAccounts}"><c:set var="chargeFormId" value="productChargePolicyForm" /><%@ include file="../fragments/loan-product-charge-fields.jspf" %></c:if>
                        <c:if test="${not canSetChargeAccounts}">
                            <p class="text-sm md:col-span-2"><spring:message code="charges.postingSetupPermission" /></p>
                        </c:if>
                    </div>
                </section>

                <section class="product-builder-section product-config-step" data-product-config-step="evidence"><div class="product-builder-section-header"><h2><spring:message code="recording.attachments" /></h2></div><div class="product-builder-section-body" data-product-config-panel hidden>
<label><input type="checkbox" name="disbursementProofRequired" value="true" ${product.disbursementProofRequired ? 'checked' : ''} /><spring:message code="admin.settings.disbursementProofRequired" text="Disbursement proof" /></label>
<label><input type="checkbox" name="applicantAttachmentRequired" value="true" ${product.applicantAttachmentRequired ? 'checked' : ''} /><spring:message code="applications.signedApplication" text="Signed application" /></label>
</div><label><spring:message code="recording.guarantors"/><input name="guarantorsRequired" type="number" min="0" max="15" value="${product.guarantorsRequired}" class="aws-control"/></label>
<details class="workflow-subsection product-config-subsection">
                            <summary class="workflow-subsection-title">Applicant documents</summary>
                            <div class="product-config-subsection-body">
                            <div class="workflow-support-grid">
                                <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    <input name="applicantAttachmentRequired" type="checkbox" value="true" ${product.applicantAttachmentRequired ? 'checked' : ''} data-required-attachments-toggle />
                                    <span><spring:message code="admin.settings.requireApplicantAttachment" text="Require applicant attachment while applying" /></span>
                                </label>
                                <input type="hidden" name="applicantAttachmentRequired" value="false" />
                            </div>
                            <div class="mt-3 ${product.applicantAttachmentRequired ? '' : 'hidden '} border border-slate-200 bg-white p-3" data-required-attachments-panel>
                                <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
                                    <div>
                                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Required Attachments</p>
                                        <p class="mt-1 text-xs text-slate-500">Create one record for each document the applicant must upload.</p>
                                    </div>
                                    <button type="button" class="app-btn btn-neutral" data-required-attachment-add>Add Name of Required Attachment</button>
                                </div>
                                <div class="hidden border border-slate-200 bg-slate-50 p-3" data-required-attachment-form>
                                    <div class="grid gap-3 md:grid-cols-[minmax(0,1fr)_10rem_auto_auto] md:items-end" data-table-toolbar-for="admin-loan-product-edit-4" >
                                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                            Attachment Name
                                            <input form="requiredAttachmentCreateForm" name="attachmentName" type="text" maxlength="120" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="e.g. Salary slip" />
                                        </label>
                                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                            Max MB
                                            <input form="requiredAttachmentCreateForm" name="maxSizeMb" type="number" min="0.01" max="100" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="5.00" />
                                        </label>
                                        <button form="requiredAttachmentCreateForm" type="submit" class="app-btn btn-launch">Create</button>
                                        <button type="button" class="app-btn btn-neutral" data-required-attachment-cancel>Cancel</button>
                                    </div>
                                </div>
                                <div class="erp-table-wrap mt-4" data-aws-table-region data-loading-label="Loading clients..." data-resource-table="admin-loan-product-edit-4" data-resource-split data-table-title-id="admin-loan-product-edit-title">
                                    <div class="erp-table-scroll erp-table-scroll-sm">
                                    <table data-table-sort-mode="none" data-table-sort-reason="editor" class="erp-table min-w-[620px]">
                                        <thead>
                                        <tr>
                                            <th class="px-3 py-2 text-left">Name</th>
                                            <th class="px-3 py-2 text-left">Max MB</th>
                                            <th class="px-3 py-2 text-left" data-table-actions-header>Action</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        <c:choose>
                                            <c:when test="${empty requiredAttachments}">
                                                <tr>
                                                    <td colspan="3" class="px-4 py-5 text-sm text-slate-500">No required attachments have been created yet.</td>
                                                </tr>
                                            </c:when>
                                            <c:otherwise>
                                                <c:forEach items="${requiredAttachments}" var="attachmentRequirement">
                                                    <tr>
                                                        <td class="px-3 py-2 font-semibold text-slate-800"><c:out value="${attachmentRequirement.attachmentName}" /></td>
                                                        <td class="px-3 py-2"><fmt:formatNumber value="${attachmentRequirement.maxSizeMb}" minFractionDigits="2" maxFractionDigits="2" /></td>
                                                        <td class="px-3 py-2">
                                                            <button form="deleteRequiredAttachment-${attachmentRequirement.id}" type="submit" class="app-btn btn-reject">Delete</button>
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                            </c:otherwise>
                                        </c:choose>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                            </div>
                            </div>
                        </details></section>



                <div class="product-config-actionbar">
                    <div class="product-config-section-actions" hidden aria-label="Configuration section navigation">
                        <button type="button" class="app-btn btn-neutral" data-product-config-previous disabled>Previous section</button>
                        <button type="button" class="app-btn btn-neutral" data-product-config-next>Next section</button>
                    </div>
                    <div class="product-config-save-actions">
                        <span class="product-config-save-state" hidden data-product-config-save-state data-unsaved-label="<spring:message code='admin.workspace.unsaved' />"></span>
                        <a href="/admin/settings-controls?section=loan" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a>
                        <button type="submit" class="app-btn btn-launch"><spring:message code="admin.workspace.saveProduct" /></button>
                </div>
    </div>
        </form>
        <c:if test="${canSetChargeAccounts}"><form id="productChargePolicyForm" method="post" action="<c:url value='/admin/settings-controls/loan-products/${product.id}/charge-accounts' />" data-workspace-submit="recover" data-product-charge-form>
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        </form></c:if>
        <sec:authorize access="@access.has(principal,'APPROVAL_FLOW_VIEW')">
        <section class="product-builder-section product-config-step" data-product-config-step="workflow">
            <div class="product-builder-section-header"><h2><spring:message code="admin.settings.workflow.title" /></h2></div>
            <div class="product-builder-section-body" data-product-config-panel hidden>
                <form method="post" action="<c:url value='/admin/settings-controls/loan-products/${product.id}/approval' />" data-workspace-submit="recover" data-product-approval-form>
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <%@ include file="../fragments/loan-product-approval-fields.jspf" %>
                    <sec:authorize access="@access.has(principal,'APPROVAL_FLOW_CONFIGURE')"><div class="accounting-actions"><button class="app-btn btn-launch"><spring:message code="common.save" /></button></div></sec:authorize>
                </form>
            </div>
        </section>
        </sec:authorize>
</div>
</div>

<form id="requiredAttachmentCreateForm" action="/admin/settings-controls/loan-products/${product.id}/required-attachments" method="post" class="hidden">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
</form>
<c:forEach items="${requiredAttachments}" var="attachmentRequirement">
    <form id="deleteRequiredAttachment-${attachmentRequirement.id}" action="/admin/settings-controls/loan-products/${product.id}/required-attachments/${attachmentRequirement.id}/delete" method="post" class="hidden">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    </form>
</c:forEach>

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




<%@ include file="../fragments/confirm-modal.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/loan-tenure-config.css' />" /><script src="<c:url value='/js/loan-tenure-config.js' />" defer></script><script src="<c:url value='/js/loan-recording.js' />" defer></script><%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/loan-product-editor-script.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
