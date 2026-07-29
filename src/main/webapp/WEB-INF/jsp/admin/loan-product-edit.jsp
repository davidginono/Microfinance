<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/modal-shell.jspf" %>
<style>
    .product-builder-form {
        display: grid;
        gap: 1rem;
    }
    .product-builder-section {
        border: 1px solid #d7dde3;
        border-radius: 0.4rem;
        background: #ffffff;
        overflow: hidden;
    }
    .product-builder-section-header {
        padding: 0.95rem 1rem 0.85rem;
        border-bottom: 1px solid #e2e8f0;
        background: #f8fafc;
    }
    .product-builder-section-body {
        padding: 1rem;
    }
    .product-builder-grid {
        display: grid;
        gap: 1rem;
    }
    .product-identity-grid {
        display: grid;
        gap: 1rem;
        align-items: end;
    }
    .product-identity-grid input:not([type="checkbox"]):not([type="radio"]),
    .product-identity-grid select {
        height: 3.5rem;
    }
    .savings-ratio-grid {
        display: flex;
        flex-wrap: wrap;
        gap: 1rem;
        align-items: flex-end;
    }
    .savings-ratio-field {
        flex: 0 1 18rem;
        min-width: 13rem;
    }
    .savings-ratio-grid .settings-checkbox-card {
        flex: 1 1 22rem;
    }
    .savings-multiplier-control {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
        align-items: center;
    }
    .savings-multiplier-control input {
        flex: 0 1 10rem;
    }
    .savings-multiplier-control.hidden {
        display: none !important;
    }
    .savings-multiplier-label {
        min-height: 3rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        color: #64748b;
        font-weight: 700;
        text-transform: none;
        letter-spacing: 0;
    }
    .settings-checkbox-card {
        min-height: 3.5rem;
    }
    @media (max-width: 640px) {
        .savings-ratio-grid {
            align-items: stretch;
        }
        .savings-ratio-field,
        .savings-ratio-grid .settings-checkbox-card {
            flex-basis: 100%;
        }
    }
    .product-builder-form input:not([type="checkbox"]):not([type="radio"]),
    .product-builder-form select {
        width: 100%;
        min-height: 3.5rem;
    }
    .product-builder-form textarea {
        width: 100%;
        min-height: 7.25rem;
    }
    .settings-action-bar {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.75rem;
        border: 1px solid #e2e8f0;
        border-radius: 0.4rem;
        background: #f8fafc;
        padding: 0.75rem;
        min-width: 0;
        max-width: 100%;
    }
    .settings-action-bar {
        align-items: flex-end;
        justify-content: space-between;
    }
    .settings-action-bar .neo-select-button,
    .settings-action-bar select,
    .settings-action-button {
        box-sizing: border-box;
        height: 3.5rem;
    }
    .settings-action-note {
        flex: 1 1 22rem;
        min-width: 0;
    }
    .settings-action-button {
        flex: 0 0 auto;
        min-width: 11rem;
        justify-content: center;
    }
    .settings-action-bar--end {
        justify-content: flex-end;
    }
    .settings-action-bar--split {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        align-items: center;
    }
    .settings-action-bar--split .settings-action-button {
        justify-self: end;
    }
    @media (max-width: 639px) {
        .settings-action-bar--split {
            grid-template-columns: 1fr;
        }
        .settings-action-button {
            flex-basis: 100%;
        }
        .settings-action-bar--split .settings-action-button {
            justify-self: start;
        }
    }
    .workflow-stage-list {
        display: grid;
        gap: 0.75rem;
    }
    .workflow-stage-row {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) auto;
        gap: 0.85rem;
        align-items: start;
        padding: 0.9rem 1rem;
        border: 1px solid #d7dde3;
        border-radius: 0.4rem;
        background: #ffffff;
    }
    .workflow-stage-priority {
        min-width: 3rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 9999px;
        background: #e8f4f8;
        color: #24556a;
        font-size: 0.78rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        padding: 0.35rem 0.7rem;
    }
    .workflow-stage-copy {
        min-width: 0;
    }
    .workflow-stage-title {
        color: #0f172a;
        font-size: 0.95rem;
        font-weight: 700;
    }
    .workflow-stage-note {
        margin-top: 0.3rem;
        color: #64748b;
        font-size: 0.84rem;
        line-height: 1.5;
    }
    .workflow-stage-state {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        border-radius: 9999px;
        padding: 0.35rem 0.75rem;
        font-size: 0.78rem;
        font-weight: 700;
        white-space: nowrap;
    }
    .workflow-state-required,
    .workflow-state-mandatory {
        background: #ecfdf5;
        color: #166534;
    }
    .workflow-state-configured {
        background: #eff6ff;
        color: #1d4ed8;
    }
    .workflow-state-skipped {
        background: #f1f5f9;
        color: #475569;
    }
    .workflow-state-missing {
        background: #fff1f2;
        color: #be123c;
    }
    .workflow-warning-stack {
        display: grid;
        gap: 0.7rem;
    }
    .workflow-warning {
        border: 1px solid #d7dde3;
        border-radius: 0.4rem;
        padding: 0.85rem 0.95rem;
        font-size: 0.88rem;
        line-height: 1.5;
    }
    .workflow-warning.is-info {
        border-color: #cfe5ee;
        background: #f3fbfe;
        color: #0f4c5f;
    }
    .workflow-warning.is-warn {
        border-color: #fde68a;
        background: #fffbeb;
        color: #92400e;
    }
    .workflow-warning.is-danger {
        border-color: #fecdd3;
        background: #fff1f2;
        color: #9f1239;
    }
    .field-error-input {
        border-color: #f43f5e !important;
        background: #fff7f8 !important;
        box-shadow: 0 0 0 1px rgba(244, 63, 94, 0.15);
    }
    .field-error-container {
        color: #9f1239;
    }
    .workflow-table-cell.field-error-container,
    .workflow-start-grid.field-error-container,
    .workflow-checkbox-lock.field-error-container {
        background: #fff7f8;
    }
    .workflow-start-grid.field-error-container {
        border: 1px solid #fecdd3;
        border-radius: 0.65rem;
        padding: 0.85rem;
    }
    .workflow-table-cell.field-error-container {
        border-radius: 0.65rem;
    }
    .workflow-checkbox-lock.field-error-container {
        border-color: #fecdd3;
        box-shadow: 0 0 0 1px rgba(244, 63, 94, 0.12);
    }
    .field-error-text {
        margin-top: 0.45rem;
        color: #be123c;
        font-size: 0.8rem;
        font-weight: 600;
        line-height: 1.45;
        text-transform: none;
        letter-spacing: normal;
    }
    .workflow-preview-shell {
        border: 1px solid #d7dde3;
        border-radius: 0.4rem;
        background: linear-gradient(180deg, #f8fbfc 0%, #ffffff 100%);
        padding: 1rem;
    }
    .workflow-preview-runtime {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.45rem;
    }
    .workflow-preview-chip {
        display: inline-flex;
        align-items: center;
        border-radius: 9999px;
        background: #ffffff;
        border: 1px solid #d7dde3;
        padding: 0.35rem 0.75rem;
        color: #0f172a;
        font-size: 0.82rem;
        font-weight: 700;
    }
    .workflow-preview-arrow {
        color: #94a3b8;
        font-size: 0.9rem;
    }
    .workflow-subsection + .workflow-subsection {
        margin-top: 1.25rem;
    }
    .workflow-subsection-title {
        color: #0f172a;
        font-size: 0.9rem;
        font-weight: 700;
        margin-bottom: 0.75rem;
    }
    .workflow-start-grid {
        display: grid;
        gap: 0.75rem;
    }
    .workflow-start-option {
        display: flex;
        gap: 0.7rem;
        align-items: flex-start;
        border: 1px solid #d7dde3;
        border-radius: 0.4rem;
        background: #ffffff;
        padding: 0.9rem 1rem;
        color: #334155;
        font-size: 0.88rem;
    }
    .workflow-start-option input {
        margin-top: 0.1rem;
    }
    .workflow-table {
        border: 1px solid #d7dde3;
        border-radius: 0.4rem;
        overflow: hidden;
        background: #ffffff;
    }
    .workflow-table-head,
    .workflow-table-row {
        display: grid;
        grid-template-columns: minmax(0, 1.25fr) minmax(120px, 0.8fr) minmax(110px, 0.7fr) minmax(0, 1.5fr);
        gap: 1rem;
        align-items: start;
        padding: 0.9rem 1rem;
    }
    .workflow-table-head {
        background: #f8fafc;
        border-bottom: 1px solid #d7dde3;
        color: #64748b;
        font-size: 0.76rem;
        font-weight: 700;
        letter-spacing: 0.08em;
        text-transform: uppercase;
    }
    .workflow-table-row + .workflow-table-row {
        border-top: 1px solid #e2e8f0;
    }
    .workflow-table-cell {
        min-width: 0;
        color: #0f172a;
        font-size: 0.9rem;
    }
    .workflow-table-stage {
        font-weight: 700;
    }
    .workflow-checkbox-lock {
        display: inline-flex;
        align-items: center;
        gap: 0.45rem;
        color: #334155;
        font-size: 0.88rem;
        font-weight: 600;
    }
    .workflow-priority-select {
        width: 100%;
        max-width: 6rem;
        min-height: 3.5rem;
        border: 1px solid #cbd5e1;
        border-radius: 0.4rem;
        background: #f8fafc;
        color: #334155;
        font-size: 0.88rem;
        padding: 0.45rem 0.65rem;
    }
    .workflow-stage-meta {
        display: inline-flex;
        align-items: center;
        border-radius: 9999px;
        padding: 0.25rem 0.65rem;
        font-size: 0.76rem;
        font-weight: 700;
        margin-top: 0.55rem;
    }
    .workflow-support-grid {
        display: grid;
        gap: 0.75rem;
    }
    @media (min-width: 768px) {
        .product-builder-grid.two-up {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
        .product-identity-grid {
            grid-template-columns: minmax(8rem, 0.55fr) minmax(16rem, 1.5fr) minmax(12rem, 0.8fr);
        }
        .product-identity-description {
            grid-column: 1 / -1;
        }
        .savings-ratio-grid {
            grid-template-columns: minmax(0, 1fr);
        }
        .workflow-start-grid {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
        .workflow-support-grid {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
    }
    @media (max-width: 640px) {
        .workflow-stage-row {
            grid-template-columns: minmax(0, 1fr);
        }
        .workflow-table-head {
            display: none;
        }
        .workflow-table-row {
            grid-template-columns: minmax(0, 1fr);
            gap: 0.6rem;
        }
        .workflow-table-cell::before {
            content: attr(data-label);
            display: block;
            margin-bottom: 0.2rem;
            color: #64748b;
            font-size: 0.72rem;
            font-weight: 700;
            letter-spacing: 0.08em;
            text-transform: uppercase;
        }
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Settings & Controls / Loan Products</p>
    <h1 class="erp-page-title">Edit Loan Product</h1>
    <p class="erp-page-subtitle">Update product rules using the same layout and validation as the Settings modal.</p>
</div>

<div class="mb-4 flex flex-wrap justify-between gap-3">
    <a href="/admin/settings-controls?section=loan" class="app-btn btn-neutral">Back to loan products</a>
    <form action="/admin/settings-controls/loan-products/${product.id}/delete"
          method="post"
          data-confirm-eyebrow="Confirm Deletion"
          data-confirm-title="Delete Loan Product"
          data-confirm-message="Delete this loan product from active settings? Historical loan applications and reports will remain preserved."
          data-confirm-proceed="Delete Product">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <button type="submit" class="app-btn btn-reject">Delete Product</button>
    </form>
</div>

<form action="/admin/settings-controls/${product.id}"
                  method="post"
                  class="product-builder-form"
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
                    <div class="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">${message}</div>
                </c:if>
                <c:if test="${openProductModalKey eq productModalKey and not empty error}">
                    <div class="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">${error}</div>
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

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.minRepaymentMonths" text="Min Repayment Months" />
                            <input name="minRepaymentMonths" type="number" min="1" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.minimumRepaymentMonths}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.maxRepaymentMonths" text="Max Repayment Months" />
                            <input name="maxRepaymentMonths" type="number" min="1" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maxRepaymentMonths}" />
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
                            <div class="mt-3 ${product.applicantAttachmentRequired ? '' : 'hidden '}rounded-md border border-slate-200 bg-white p-3" data-required-attachments-panel>
                                <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
                                    <div>
                                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Required Attachments</p>
                                        <p class="mt-1 text-xs text-slate-500">Create one record for each document the applicant must upload.</p>
                                    </div>
                                    <button type="button" class="app-btn btn-neutral" data-required-attachment-add>Add Name of Required Attachment</button>
                                </div>
                                <div class="hidden rounded-md border border-slate-200 bg-slate-50 p-3" data-required-attachment-form>
                                    <div class="grid gap-3 md:grid-cols-[minmax(0,1fr)_10rem_auto_auto] md:items-end">
                                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                            Attachment Name
                                            <input form="requiredAttachmentCreateForm" name="attachmentName" type="text" maxlength="120" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="e.g. Salary slip" />
                                        </label>
                                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                            Max MB
                                            <input form="requiredAttachmentCreateForm" name="maxSizeMb" type="number" min="0.01" max="100" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="5.00" />
                                        </label>
                                        <button form="requiredAttachmentCreateForm" type="submit" class="app-btn btn-primary">Create</button>
                                        <button type="button" class="app-btn btn-neutral" data-required-attachment-cancel>Cancel</button>
                                    </div>
                                </div>
                                <div class="mt-4 max-h-72 overflow-y-auto rounded-md border border-slate-200">
                                    <table class="erp-table min-w-[620px]">
                                        <thead>
                                        <tr>
                                            <th class="px-3 py-2 text-left">Name</th>
                                            <th class="px-3 py-2 text-left">Max MB</th>
                                            <th class="px-3 py-2 text-left">Action</th>
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

                <div class="flex flex-wrap justify-end gap-3">
                    <a href="/admin/settings-controls?section=loan" class="app-btn btn-neutral">Cancel</a>
                    <button type="submit" class="app-btn btn-primary"><spring:message code="common.save" text="Save" /></button>
                </div>
</form>

<form id="requiredAttachmentCreateForm" action="/admin/settings-controls/loan-products/${product.id}/required-attachments" method="post" class="hidden">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
</form>
<c:forEach items="${requiredAttachments}" var="attachmentRequirement">
    <form id="deleteRequiredAttachment-${attachmentRequirement.id}" action="/admin/settings-controls/loan-products/${product.id}/required-attachments/${attachmentRequirement.id}/delete" method="post" class="hidden">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    </form>
</c:forEach>

<spring:message code="admin.settings.workflow.warning.noReviewStage" text="At least one review or approval stage must be enabled before disbursement." var="warningNoReviewStage" />
<spring:message code="admin.settings.workflow.warning.noLoanOfficers" text="Loan Officer review is enabled for this product, but there are no active Loan Officers assigned yet." var="warningNoLoanOfficers" />
<spring:message code="admin.settings.workflow.warning.managerLoanOfficerSamePriority" text="Manager and Loan Officer cannot share the same priority." var="warningManagerLoanOfficerSamePriority" />
<spring:message code="admin.settings.workflow.warning.duplicatePriorities" text="Each enabled review stage must use a different priority." var="warningDuplicatePriorities" />
<spring:message code="admin.settings.workflow.warning.noBoardReviewers" text="Board Member review is configured, but there are no active board members available for assignment." var="warningNoBoardReviewers" />
<spring:message code="admin.settings.workflow.warning.noChairpersons" text="Chairperson review is configured, but there are no active chairpersons available for assignment." var="warningNoChairpersons" />
<spring:message code="admin.settings.workflow.warning.noCommitteeReviewers" text="Committee review is configured, but there are no active board members available for assignment." var="warningNoCommitteeReviewers" />
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



<%@ include file="../fragments/confirm-modal.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
