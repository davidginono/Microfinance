<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
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
    .product-builder-form input:not([type="checkbox"]):not([type="radio"]),
    .product-builder-form select {
        width: 100%;
        min-height: 3.5rem;
    }
    .product-builder-form textarea {
        width: 100%;
        min-height: 7.25rem;
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
    .workflow-readonly-note {
        border: 1px solid #dbe3ec;
        border-radius: 0.4rem;
        background: #f8fafc;
        padding: 0.85rem 0.95rem;
        color: #475569;
        font-size: 0.88rem;
        line-height: 1.5;
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
    <p class="erp-breadcrumb">Admin Tools / Settings & Controls</p>
    <h1 class="erp-page-title">Settings & Controls</h1>
    <p class="erp-page-subtitle">Manage loan, guarantor, and board controls.</p>
</div>

<c:if test="${not empty openProductModalKey}">
    <div hidden data-open-product-modal="${fn:escapeXml(openProductModalKey)}"></div>
</c:if>

<c:if test="${settingsSection eq 'board'}">
    <div class="erp-panel mb-4">
        <div class="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
                <p class="erp-widget-title">Approval Flow</p>
                <h2 class="mt-1 text-xl font-bold text-sacco-ink">Tenant Approval Configuration</h2>
                <p class="mt-1 text-sm text-slate-500">Manager review always starts the approval flow. Accountant review and Disbursement Officer release remain mandatory end stages.</p>
            </div>
        </div>
        <form action="/admin/settings-controls/review-rules" method="post" class="erp-panel-body grid gap-4">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />

            <div class="grid gap-4 lg:grid-cols-2">
                <label class="flex items-start gap-3 rounded-lg border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                    <input name="loanOfficerReviewRequired" type="checkbox" value="true" class="mt-1" ${settings.loanOfficerReviewRequired ? 'checked' : ''} />
                    <span>
                        <span class="block font-semibold text-slate-900">Require Loan Officer Review</span>
                        <span class="mt-1 block text-slate-500">Adds a single assigned Loan Officer stage between Manager and Board or Accountant.</span>
                    </span>
                </label>
                <label class="flex items-start gap-3 rounded-lg border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                    <input name="boardReviewRequired" type="checkbox" value="true" class="mt-1" ${settings.boardReviewRequired ? 'checked' : ''} />
                    <span>
                        <span class="block font-semibold text-slate-900">Require Board / Credit Committee Review</span>
                        <span class="mt-1 block text-slate-500">Adds the committee stage before Accountant review for SACCOs that need group approval.</span>
                    </span>
                </label>
            </div>

            <div class="grid gap-4 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]">
                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Required Board Members Per Review
                    <input name="boardQuorum"
                           type="number"
                           min="1"
                           max="${activeBoardMemberCount gt 0 ? activeBoardMemberCount : 1}"
                           class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800"
                           value="${settings.boardQuorum}" />
                    <span class="mt-2 block text-sm font-normal normal-case tracking-normal text-slate-500">
                        Used only when Board / Credit Committee review is enabled.
                    </span>
                </label>

                <div class="rounded-lg border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-700">
                    <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Resolved Flow</p>
                    <div class="mt-3 flex flex-wrap items-center gap-2">
                        <c:forEach items="${approvalFlowStageLabels}" var="stage" varStatus="status">
                            <span class="rounded-full bg-white px-3 py-1 font-semibold text-slate-800 ring-1 ring-slate-200">${stage}</span>
                            <c:if test="${not status.last}">
                                <span class="text-slate-400">→</span>
                            </c:if>
                        </c:forEach>
                    </div>
                    <p class="mt-3 text-xs text-slate-500">Examples: Branch Manager → Accountant → Disbursement Officer, or Branch Manager → Loan Officer → Board / Credit Committee → Accountant → Disbursement Officer.</p>
                </div>
            </div>

            <div class="grid gap-3 md:grid-cols-4">
                <div class="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
                    Active Loan Officers: <strong>${activeLoanOfficerCount}</strong>
                </div>
                <div class="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
                    Active Board Members: <strong>${activeBoardMemberCount}</strong>
                </div>
                <div class="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
                    Active Accountants: <strong>${activeAccountantCount}</strong>
                </div>
                <div class="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
                    Active Disbursement Officers: <strong>${activeDisbursementOfficerCount}</strong>
                </div>
            </div>

            <div class="app-modal-actions !justify-start md:justify-end">
                <button type="submit" class="app-btn btn-primary">Save</button>
            </div>
        </form>
    </div>
</c:if>

<c:if test="${settingsSection eq 'loan'}">
    <div class="mb-4 flex flex-wrap justify-end gap-3">
        <button type="button"
                class="app-btn btn-neutral"
                data-product-modal-open="application-fee">
            Edit application fee
        </button>
        <c:if test="${not empty loanProductsVersions}">
            <button type="button"
                    class="app-btn btn-neutral"
                    data-product-modal-open="loan-products-versions">
                Loan Products Versions
            </button>
        </c:if>
        <c:if test="${not customizedProductExists}">
            <button type="button"
                    class="app-btn btn-primary"
                    data-product-modal-open="create-product">
                Add loan product
            </button>
        </c:if>
    </div>

    <div class="grid gap-4 xl:grid-cols-2 2xl:grid-cols-3">
        <c:forEach items="${products}" var="product">
            <c:set var="productLoanOfficerEnabled"
                   value="${product.loanOfficerReviewRequired == true || (product.loanOfficerReviewRequired == null && settings.loanOfficerReviewRequired)}" />
            <c:set var="productWorkflowStartStage"><c:out value="${product.resolvedWorkflowStartStage}" /></c:set>
            <section class="erp-panel overflow-hidden">
                <div class="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                        <p class="erp-widget-title">Loan Product</p>
                        <h2 class="mt-1 text-xl font-bold text-sacco-ink"><c:out value="${product.displayName}" /></h2>
                        <p class="mt-1 text-sm text-slate-500"><c:out value="${product.displayDescription}" /></p>
                    </div>
                    <div class="flex items-center gap-2 sm:pt-1">
                        <span class="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold
                            ${product.status eq 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : ''}
                            ${product.status eq 'DRAFT' ? 'bg-amber-50 text-amber-700' : ''}
                            ${product.status eq 'SUSPENDED' ? 'bg-slate-100 text-slate-600' : ''}
                            ${product.status eq 'RETIRED' ? 'bg-rose-50 text-rose-700' : ''}">
                            ${product.status}
                        </span>
                        <button type="button"
                                class="app-btn btn-neutral"
                                data-product-modal-open="versions-${product.id}">
                            Versions
                        </button>
                        <button type="button"
                                class="app-btn btn-primary"
                                data-product-modal-open="product-${product.id}">
                            Edit
                        </button>
                    </div>
                </div>

                <div class="divide-y divide-slate-200">
                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Display Order</p>
                            <p class="text-sm text-slate-500">Controls how products appear across the workspace.</p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">${product.resolvedDisplayOrder}</p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Amount Range</p>
                            <p class="text-sm text-slate-500">Minimum and maximum loan amount allowed for this product.</p>
                        </div>
                        <p class="text-right text-base font-semibold text-slate-900">
                            <fmt:formatNumber value="${product.minimumAmount}" minFractionDigits="2" maxFractionDigits="2" />
                            to
                            <c:choose>
                                <c:when test="${product.maximumAmount ne null}">
                                    <fmt:formatNumber value="${product.maximumAmount}" minFractionDigits="2" maxFractionDigits="2" />
                                </c:when>
                                <c:otherwise>Not set</c:otherwise>
                            </c:choose>
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Savings Percentage</p>
                            <p class="text-sm text-slate-500">Maximum percentage of member savings available for this product.</p>
                        </div>
                        <p class="text-base font-semibold text-slate-900"><fmt:formatNumber value="${product.maxLoanSavingsRatio * 100}" minFractionDigits="2" maxFractionDigits="2" />%</p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Interest Method</p>
                            <p class="text-sm text-slate-500">Method and annual rate applied during repayment calculations.</p>
                        </div>
                        <p class="text-right text-base font-semibold text-slate-900">
                            ${product.interestMethod}
                            <span class="block text-sm font-medium text-slate-600"><fmt:formatNumber value="${product.interestRate * 100}" minFractionDigits="2" maxFractionDigits="2" />%</span>
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Tenure Rules</p>
                            <p class="text-sm text-slate-500">Minimum and maximum repayment duration allowed for this product.</p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">${product.minimumRepaymentMonths} - ${product.maxRepaymentMonths} month(s)</p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Guarantors</p>
                            <p class="text-sm text-slate-500">Number of guarantors required before review moves forward.</p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">${product.guarantorsRequired}</p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Application With Active Loan</p>
                            <p class="text-sm text-slate-500">Controls whether members can apply again while a disbursed loan is still open.</p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">${product.applicationWithActiveLoanAllowed ? 'Allowed' : 'Blocked'}</p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Loaded Financial Data</p>
                            <p class="text-sm text-slate-500">Controls whether the member must load current financial data before the application can move forward.</p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">${product.freshFinancialDataRequired ? 'Required' : 'Optional'}</p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Priority Flow</p>
                            <p class="text-sm text-slate-500">Saved approval order used in the product workflow preview.</p>
                        </div>
                        <div class="text-right">
                            <span class="block text-sm font-semibold text-emerald-700">
                                ${productWorkflowStartStage eq 'LOAN_OFFICER' ? 'P1 Loan Officer' : 'P1 Manager'}
                            </span>
                            <span class="mt-1 block text-sm font-semibold ${productLoanOfficerEnabled ? 'text-emerald-700' : 'text-slate-500'}">
                                <c:choose>
                                    <c:when test="${productLoanOfficerEnabled}">
                                        ${productWorkflowStartStage eq 'LOAN_OFFICER' ? 'P2 Manager' : 'P2 Loan Officer'}
                                    </c:when>
                                    <c:otherwise>P2 Loan Officer Skipped</c:otherwise>
                                </c:choose>
                            </span>
                            <span class="mt-1 block text-sm font-semibold ${product.committeeReviewRequired ? 'text-emerald-700' : 'text-slate-500'}">
                                P${product.resolvedCommitteePriority} Committee ${product.committeeReviewRequired ? 'Configured' : 'Skipped'}
                            </span>
                            <span class="mt-1 block text-sm font-semibold text-emerald-700">P${product.resolvedAccountantPriority} Accountant Mandatory</span>
                            <span class="mt-1 block text-sm font-semibold text-emerald-700">P5 Disbursement Mandatory</span>
                        </div>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Committee Rules</p>
                            <p class="text-sm text-slate-500">Minimum votes assigned and approval threshold used for committee decisions.</p>
                        </div>
                        <p class="text-right text-base font-semibold text-slate-900">
                            ${product.committeeReviewRequired ? product.resolvedCommitteeMinimumVotes : 0} vote(s)
                            <span class="block text-sm font-medium text-slate-600">
                                Approve at ${product.committeeReviewRequired ? product.resolvedCommitteeApprovalThreshold : 0}
                            </span>
                        </p>
                    </div>
                </div>
            </section>
        </c:forEach>
    </div>
</c:if>

<c:if test="${settingsSection eq 'loan'}">
    <div class="app-modal-overlay hidden"
         data-product-modal="application-fee">
        <div class="app-modal-panel max-w-2xl">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-widget-title">Loan Controls</p>
                        <h2 class="mt-1 text-xl font-bold text-sacco-ink">Edit Application Fee</h2>
                        <p class="mt-1 text-sm text-slate-500">This fee is deducted from every loan application in this SACCO.</p>
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
                        <div class="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">${message}</div>
                    </c:if>
                    <c:if test="${openProductModalKey eq 'application-fee' and not empty error}">
                        <div class="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">${error}</div>
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
                               type="number"
                               min="0"
                               step="0.01"
                               class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800"
                               value="${settings.resolvedApplicationFee}" />
                        <span class="mt-2 block text-sm font-normal normal-case tracking-normal text-slate-500">
                            Deducted from every loan application, regardless of product type.
                        </span>
                    </label>

                    <div class="app-modal-actions">
                        <button type="button"
                                class="app-btn btn-neutral"
                                data-product-modal-close="application-fee">
                            Cancel
                        </button>
                        <button type="submit" class="app-btn btn-primary">Save</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</c:if>

<c:if test="${settingsSection eq 'loan' and not empty loanProductsVersions}">
    <div class="app-modal-overlay hidden"
         data-product-modal="loan-products-versions">
        <div class="app-modal-panel max-w-6xl">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-widget-title">Loan Products Versions</p>
                        <h2 class="mt-1 text-xl font-bold text-sacco-ink">Portfolio Snapshots</h2>
                        <p class="mt-1 text-sm text-slate-500">Each entry stores the full loan products configuration set for this SACCO at that moment.</p>
                    </div>
                    <button type="button" class="app-modal-close" data-product-modal-close="loan-products-versions" aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                </div>

                <div class="app-modal-body">
                    <div class="overflow-x-auto rounded-lg border border-slate-200">
                        <table class="min-w-full divide-y divide-slate-200 text-sm">
                            <thead class="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <tr>
                                <th class="px-4 py-3">Version</th>
                                <th class="px-4 py-3">Saved</th>
                                <th class="px-4 py-3">Saved By</th>
                                <th class="px-4 py-3">Application Fee</th>
                                <th class="px-4 py-3">Products</th>
                                <th class="px-4 py-3">Product Names</th>
                            </tr>
                            </thead>
                            <tbody class="divide-y divide-slate-200 bg-white text-slate-700">
                            <c:forEach items="${loanProductsVersions}" var="version">
                                <tr>
                                    <td class="px-4 py-3 align-top">
                                        <p class="font-semibold text-sacco-ink">V${version.versionNumber}</p>
                                        <p class="mt-1 text-xs text-slate-500">${version.snapshotType}</p>
                                    </td>
                                    <td class="px-4 py-3 align-top">${version.savedAtLabel}</td>
                                    <td class="px-4 py-3 align-top">${version.savedByLabel}</td>
                                    <td class="px-4 py-3 align-top">${version.applicationFeeLabel}</td>
                                    <td class="px-4 py-3 align-top">${version.productCount}</td>
                                    <td class="px-4 py-3 align-top">${version.productsLabel}</td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>

                    <div class="app-modal-actions">
                        <button type="button"
                                class="app-btn btn-neutral"
                                data-product-modal-close="loan-products-versions">
                            Close
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</c:if>

<c:if test="${settingsSection eq 'loan' and not customizedProductExists}">
    <div class="app-modal-overlay hidden"
         data-product-modal="create-product">
        <div class="app-modal-panel max-w-6xl">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-widget-title">Add Loan Product</p>
                        <h2 class="mt-1 text-xl font-bold text-sacco-ink">Create Loan Product</h2>
                        <p class="mt-1 text-sm text-slate-500">Set the loan name, approval path, and lending limits for this SACCO.</p>
                    </div>
                    <button type="button" class="app-modal-close" data-product-modal-close="create-product" aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                </div>

                <form action="/admin/settings-controls/customized-product"
                      method="post"
                      class="app-modal-body product-builder-form"
                      data-inline-validation-form="loan-settings"
                      data-product-workflow-builder="true"
                      data-active-loan-officers="${activeLoanOfficerCount}"
                      data-active-board-members="${activeBoardMemberCount}"
                      data-active-accountants="${activeAccountantCount}"
                      data-active-disbursement-officers="${activeDisbursementOfficerCount}"
                      data-tenant-loan-officer-enabled="${settings.loanOfficerReviewRequired}"
                      data-tenant-board-enabled="${settings.boardReviewRequired}">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="modalKey" value="create-product" />
                    <input type="hidden" name="managerReviewRequired" value="true" />
                    <input type="hidden" name="productCode" value="" data-product-code-field data-product-code-generated="true" />

                    <c:if test="${openProductModalKey eq 'create-product' and not empty message}">
                        <div class="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">${message}</div>
                    </c:if>
                    <c:if test="${openProductModalKey eq 'create-product' and not empty error}">
                        <div class="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">${error}</div>
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
                            <p class="erp-widget-title">Basics</p>
                            <h3 class="mt-1 text-lg font-bold text-sacco-ink">Product Identity</h3>
                            <p class="mt-1 text-sm text-slate-500">Set the member-facing name, display order, and status for this product.</p>
                        </div>
                        <div class="product-builder-section-body product-builder-grid two-up">
                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Display Order
                                <input name="displayOrder" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="5" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500 md:col-span-2">
                                Loan Product Name
                                <input name="productName" type="text" required maxlength="120" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="e.g. School Fees Booster" data-product-name-field />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500 md:col-span-2">
                                Description
                                <textarea name="productDescription" rows="3" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="Short description shown to members when choosing this product."></textarea>
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Product Status
                                <select name="productStatus" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                    <option value="DRAFT">Draft</option>
                                    <option value="ACTIVE" selected>Active</option>
                                    <option value="SUSPENDED">Suspended</option>
                                    <option value="RETIRED">Retired</option>
                                </select>
                            </label>
                        </div>
                    </section>

                    <section class="product-builder-section">
                        <div class="product-builder-section-header">
                            <p class="erp-widget-title">Eligibility</p>
                            <h3 class="mt-1 text-lg font-bold text-sacco-ink">Eligibility</h3>
                            <p class="mt-1 text-sm text-slate-500">Define lending limits and savings coverage for this product.</p>
                        </div>
                        <div class="product-builder-section-body product-builder-grid two-up">
                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Minimum Amount
                                <input name="minimumAmount" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="0.00" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Maximum Amount
                                <input name="maximumAmount" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="Leave blank for no product cap" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Savings Percentage
                                <input name="maxLoanSavingsPercent" type="number" min="0.01" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="33.33" />
                            </label>
                        </div>
                    </section>

                    <section class="product-builder-section">
                        <div class="product-builder-section-header">
                            <p class="erp-widget-title">Repayment</p>
                            <h3 class="mt-1 text-lg font-bold text-sacco-ink">Repayment & Charges</h3>
                            <p class="mt-1 text-sm text-slate-500">Control pricing, interest treatment, and the repayment period for this product.</p>
                        </div>
                        <div class="product-builder-section-body product-builder-grid two-up">
                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Insurance %
                                <input name="insurancePercent" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="1.50" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Annual Interest %
                                <input name="annualInterestPercent" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="10.00" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Amount Return Method
                                <select name="interestMethod" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                    <option value="FLAT_RATE" selected>Flat Rate</option>
                                    <option value="REDUCING_BALANCE">Reducing Balance</option>
                                </select>
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Min Repayment Months
                                <input name="minRepaymentMonths" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="1" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                Max Repayment Months
                                <input name="maxRepaymentMonths" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="12" />
                            </label>
                        </div>
                    </section>

                    <section class="product-builder-section">
                        <div class="product-builder-section-header">
                            <p class="erp-widget-title">Approval Workflow</p>
                            <h3 class="mt-1 text-lg font-bold text-sacco-ink">Approval Workflow</h3>
                            <p class="mt-1 text-sm text-slate-500">Configure the approval path for this loan product.</p>
                        </div>
                        <div class="product-builder-section-body">
                            <div class="workflow-readonly-note">
                                New applications follow this saved workflow configuration. Applications already on review keep the workflow that was active when they were submitted.
                            </div>

                            <div class="workflow-subsection mt-4">
                                <p class="workflow-subsection-title">Start Stage</p>
                                <div class="workflow-start-grid" data-field-group="workflowStartStage">
                                    <label class="workflow-start-option">
                                        <input type="radio" name="workflowStartStage" value="MANAGER" checked data-workflow-start-radio="manager" />
                                        <span>
                                            <span class="block font-semibold text-slate-900">Manager First</span>
                                            <span class="mt-1 block text-slate-500">Use this when the manager should lead the first review step.</span>
                                        </span>
                                    </label>
                                    <label class="workflow-start-option">
                                        <input type="radio" name="workflowStartStage" value="LOAN_OFFICER" data-workflow-start-radio="loanOfficer" />
                                        <span>
                                            <span class="block font-semibold text-slate-900">Loan Officer First</span>
                                            <span class="mt-1 block text-slate-500">Use this when the loan officer should review before the manager.</span>
                                        </span>
                                    </label>
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title">Review Stages</p>
                                <div class="workflow-table">
                                    <div class="workflow-table-head">
                                        <div>Stage</div>
                                        <div>Required</div>
                                        <div>Priority</div>
                                        <div>Notes</div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label="Stage">Manager</div>
                                        <div class="workflow-table-cell" data-label="Required">
                                            <label class="workflow-checkbox-lock"><input type="checkbox" checked disabled aria-label="Manager required" /></label>
                                        </div>
                                        <div class="workflow-table-cell" data-label="Priority">
                                            <select name="managerPriority" class="workflow-priority-select" data-workflow-manager-priority>
                                                <option value="1" selected>1</option>
                                                <option value="2">2</option>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label="Notes">Set this to 1 or 2 when both Manager and Loan Officer are part of the review path.</div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label="Stage">Loan Officer</div>
                                        <div class="workflow-table-cell" data-label="Required">
                                            <label class="workflow-checkbox-lock"><input name="loanOfficerReviewRequired" type="checkbox" value="true" ${settings.loanOfficerReviewRequired ? 'checked' : ''} data-workflow-loan-officer aria-label="Loan Officer required" /></label>
                                        </div>
                                        <div class="workflow-table-cell" data-label="Priority">
                                            <select name="loanOfficerPriority" class="workflow-priority-select" data-workflow-loan-officer-priority>
                                                <option value="1">1</option>
                                                <option value="2" selected>2</option>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label="Notes">
                                            <c:choose>
                                                <c:when test="${settings.loanOfficerReviewRequired}">
                                                    Set this to 1 or 2. It must not match the Manager priority.
                                                </c:when>
                                                <c:otherwise>
                                                    Enable Loan Officer review in tenant approval flow settings to use this stage.
                                                </c:otherwise>
                                            </c:choose>
                                        </div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label="Stage">Committee</div>
                                        <div class="workflow-table-cell" data-label="Required">
                                            <label class="workflow-checkbox-lock"><input name="committeeReviewRequired" type="checkbox" value="true" checked data-workflow-committee aria-label="Committee required" /></label>
                                        </div>
                                        <div class="workflow-table-cell" data-label="Priority">
                                            <select name="committeePriority" class="workflow-priority-select" data-workflow-committee-priority>
                                                <option value="3" selected>3</option>
                                                <option value="4">4</option>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label="Notes">
                                            Optional stage used when this product must wait for committee decisions.
                                        </div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label="Stage">Accountant</div>
                                        <div class="workflow-table-cell" data-label="Required">
                                            <label class="workflow-checkbox-lock"><input name="accountantReviewRequired" type="checkbox" value="true" checked data-workflow-accountant aria-label="Accountant required" /></label>
                                        </div>
                                        <div class="workflow-table-cell" data-label="Priority">
                                            <select name="accountantPriority" class="workflow-priority-select" data-workflow-accountant-priority>
                                                <option value="3">3</option>
                                                <option value="4" selected>4</option>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label="Notes">
                                            Final financial verification before release.
                                        </div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label="Stage">Disbursement Officer</div>
                                        <div class="workflow-table-cell" data-label="Required">
                                            <label class="workflow-checkbox-lock"><input type="checkbox" checked disabled aria-label="Disbursement Officer required" /></label>
                                        </div>
                                        <div class="workflow-table-cell" data-label="Priority">
                                            <select class="workflow-priority-select" disabled><option selected>5</option></select>
                                        </div>
                                        <div class="workflow-table-cell" data-label="Notes">
                                            Final release stage.
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title">Committee Rules</p>
                                <div class="workflow-support-grid">
                                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                        Committee Reviewers Assigned
                                        <input name="committeeMinimumVotes" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="2" data-workflow-committee-votes />
                                    </label>
                                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                        Committee Approvals Needed
                                        <input name="committeeApprovalThreshold" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="2" data-workflow-committee-threshold />
                                    </label>
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title">Eligibility Support</p>
                                <div class="workflow-support-grid">
                                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                        Guarantors Required
                                        <input name="guarantorsRequired" type="number" min="0" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="2" data-workflow-guarantors />
                                    </label>
                                    <label class="flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                        <input name="freshFinancialDataRequired" type="checkbox" value="true" data-workflow-loaded-financial />
                                        <span>Require Loaded Financial Data At Submission</span>
                                    </label>
                                    <label class="flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700 md:col-span-2">
                                        <input name="allowApplicationWithActiveLoan" type="checkbox" value="true" />
                                        <span>Allow Application With Active Loan</span>
                                    </label>
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title">Validation</p>
                                <div class="workflow-warning-stack mt-4" data-workflow-warnings></div>
                            </div>
                        </div>
                    </section>

                    <section class="product-builder-section">
                        <div class="product-builder-section-header">
                            <p class="erp-widget-title">Preview</p>
                            <h3 class="mt-1 text-lg font-bold text-sacco-ink">Resolved Flow</h3>
                            <p class="mt-1 text-sm text-slate-500">The runtime path below reflects what the system would execute with current tenant settings.</p>
                        </div>
                        <div class="product-builder-section-body">
                            <div class="workflow-preview-shell">
                                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Approval Workflow</p>
                                <div class="mt-3 workflow-preview-runtime" data-workflow-preview-runtime></div>
                            </div>
                        </div>
                    </section>

                    <div class="app-modal-actions">
                        <button type="button"
                                class="app-btn btn-neutral"
                                data-product-modal-close="create-product">
                            Cancel
                        </button>
                        <button type="submit" class="app-btn btn-primary">Add loan product</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</c:if>

<c:forEach items="${products}" var="product">
    <div class="app-modal-overlay hidden"
         data-product-modal="versions-${product.id}">
        <div class="app-modal-panel max-w-6xl">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-widget-title">Product Versions</p>
                        <h2 class="mt-1 text-xl font-bold text-sacco-ink"><c:out value="${product.displayName}" /></h2>
                        <p class="mt-1 text-sm text-slate-500">Preserved configuration snapshots for this product. The most recent three versions are shown.</p>
                    </div>
                    <button type="button" class="app-modal-close" data-product-modal-close="versions-${product.id}" aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                </div>

                <div class="app-modal-body">
                    <c:set var="productVersions" value="${productVersionsByProductId[product.id]}" />
                    <c:choose>
                        <c:when test="${empty productVersions}">
                            <div class="rounded-lg border border-dashed border-slate-300 bg-slate-50 px-5 py-6 text-sm text-slate-600">
                                No saved versions are available yet. A snapshot is preserved automatically the next time this product is changed.
                            </div>
                        </c:when>
                        <c:otherwise>
                            <div class="overflow-x-auto rounded-lg border border-slate-200">
                                <table class="min-w-full divide-y divide-slate-200 text-sm">
                                    <thead class="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                                        <tr>
                                            <th class="px-4 py-3">Version</th>
                                            <th class="px-4 py-3">Saved</th>
                                            <th class="px-4 py-3">Saved By</th>
                                            <th class="px-4 py-3">Amount Range</th>
                                            <th class="px-4 py-3">Tenure</th>
                                            <th class="px-4 py-3">Interest</th>
                                            <th class="px-4 py-3">Workflow</th>
                                            <th class="px-4 py-3">Status</th>
                                            <th class="px-4 py-3 text-right">Action</th>
                                        </tr>
                                    </thead>
                                    <tbody class="divide-y divide-slate-200 bg-white text-slate-700">
                                        <c:forEach items="${productVersions}" var="version">
                                            <tr>
                                                <td class="px-4 py-3 align-top">
                                                    <p class="font-semibold text-sacco-ink">V${version.versionNumber}</p>
                                                    <p class="mt-1 text-xs text-slate-500">${version.snapshotType}</p>
                                                </td>
                                                <td class="px-4 py-3 align-top">${version.savedAtLabel}</td>
                                                <td class="px-4 py-3 align-top">${version.savedByLabel}</td>
                                                <td class="px-4 py-3 align-top">${version.amountRangeLabel}</td>
                                                <td class="px-4 py-3 align-top">${version.tenureLabel}</td>
                                                <td class="px-4 py-3 align-top">${version.interestLabel}</td>
                                                <td class="px-4 py-3 align-top">${version.workflowLabel}</td>
                                                <td class="px-4 py-3 align-top">
                                                    <span class="inline-flex rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700">
                                                        ${version.statusLabel}
                                                    </span>
                                                </td>
                                                <td class="px-4 py-3 align-top text-right">
                                                    <form action="/admin/settings-controls/${product.id}/versions/${version.id}/rollback" method="post" class="inline">
                                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                                        <button type="submit" class="app-btn btn-primary">Rollback</button>
                                                    </form>
                                                </td>
                                            </tr>
                                        </c:forEach>
                                    </tbody>
                                </table>
                            </div>
                        </c:otherwise>
                    </c:choose>

                    <div class="app-modal-actions">
                        <button type="button"
                                class="app-btn btn-neutral"
                                data-product-modal-close="versions-${product.id}">
                            Close
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div class="app-modal-overlay hidden"
         data-product-modal="product-${product.id}">
        <div class="app-modal-panel max-w-6xl">
            <div class="app-modal-scroll">
            <div class="app-modal-header">
                <div>
                    <p class="erp-widget-title">Edit Product</p>
                    <h2 class="mt-1 text-xl font-bold text-sacco-ink">Edit Loan Product: <c:out value="${product.displayName}" /></h2>
                </div>
                <button type="button" class="app-modal-close" data-product-modal-close="product-${product.id}" aria-label="Close modal">
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
                  data-active-board-members="${activeBoardMemberCount}"
                  data-active-accountants="${activeAccountantCount}"
                  data-active-disbursement-officers="${activeDisbursementOfficerCount}"
                  data-tenant-loan-officer-enabled="${settings.loanOfficerReviewRequired}"
                  data-tenant-board-enabled="${settings.boardReviewRequired}">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <input type="hidden" name="modalKey" value="product-${product.id}" />
                <input type="hidden" name="managerReviewRequired" value="true" />
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
                        <p class="erp-widget-title">Basics</p>
                        <h3 class="mt-1 text-lg font-bold text-sacco-ink">Product Identity</h3>
                        <p class="mt-1 text-sm text-slate-500">Update the member-facing name, display order, and status for this product.</p>
                    </div>
                    <div class="product-builder-section-body product-builder-grid two-up">
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Display Order
                            <input name="displayOrder" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.resolvedDisplayOrder}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500 md:col-span-2">
                            Loan Product Name
                            <input name="productName" type="text" <c:if test="${product.loanType eq 'CUSTOMIZED_LOAN'}">required</c:if> maxlength="120" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${fn:escapeXml(product.displayName)}" data-product-name-field />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500 md:col-span-2">
                            Description
                            <textarea name="productDescription" rows="3" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="Short description shown to members when choosing this product.">${fn:escapeXml(product.productDescription)}</textarea>
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Product Status
                            <select name="productStatus" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                <option value="DRAFT" ${product.status eq 'DRAFT' ? 'selected' : ''}>Draft</option>
                                <option value="ACTIVE" ${product.status eq 'ACTIVE' ? 'selected' : ''}>Active</option>
                                <option value="SUSPENDED" ${product.status eq 'SUSPENDED' ? 'selected' : ''}>Suspended</option>
                                <option value="RETIRED" ${product.status eq 'RETIRED' ? 'selected' : ''}>Retired</option>
                            </select>
                        </label>
                    </div>
                </section>

                <section class="product-builder-section">
                    <div class="product-builder-section-header">
                        <p class="erp-widget-title">Eligibility</p>
                        <h3 class="mt-1 text-lg font-bold text-sacco-ink">Eligibility</h3>
                        <p class="mt-1 text-sm text-slate-500">Update loan limits and savings coverage for this product.</p>
                    </div>
                    <div class="product-builder-section-body product-builder-grid two-up">
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Minimum Amount
                            <input name="minimumAmount" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.minimumAmount}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Maximum Amount
                            <input name="maximumAmount" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maximumAmount}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Savings Percentage
                            <input name="maxLoanSavingsPercent" type="number" min="0.01" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maxLoanSavingsRatio * 100}" />
                        </label>
                    </div>
                </section>

                <section class="product-builder-section">
                    <div class="product-builder-section-header">
                        <p class="erp-widget-title">Repayment</p>
                        <h3 class="mt-1 text-lg font-bold text-sacco-ink">Repayment & Charges</h3>
                        <p class="mt-1 text-sm text-slate-500">Update pricing, interest treatment, and repayment duration for this product.</p>
                    </div>
                    <div class="product-builder-section-body product-builder-grid two-up">
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Insurance %
                            <input name="insurancePercent" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.insuranceRate * 100}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Annual Interest %
                            <input name="annualInterestPercent" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.interestRate * 100}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Amount Return Method
                            <select name="interestMethod" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                <option value="FLAT_RATE" ${product.interestMethod eq 'FLAT_RATE' ? 'selected' : ''}>Flat Rate</option>
                                <option value="REDUCING_BALANCE" ${product.interestMethod eq 'REDUCING_BALANCE' ? 'selected' : ''}>Reducing Balance</option>
                            </select>
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Min Repayment Months
                            <input name="minRepaymentMonths" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.minimumRepaymentMonths}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Max Repayment Months
                            <input name="maxRepaymentMonths" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maxRepaymentMonths}" />
                        </label>
                    </div>
                </section>

                <section class="product-builder-section">
                    <div class="product-builder-section-header">
                        <p class="erp-widget-title">Approval Workflow</p>
                        <h3 class="mt-1 text-lg font-bold text-sacco-ink">Approval Workflow</h3>
                        <p class="mt-1 text-sm text-slate-500">Configure the approval path for this loan product.</p>
                    </div>
                    <div class="product-builder-section-body">
                        <div class="workflow-readonly-note">
                            New applications follow this saved workflow configuration. Applications already on review keep the workflow that was active when they were submitted.
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title">Review Stages</p>
                            <div class="workflow-table">
                                <div class="workflow-table-head">
                                    <div>Stage</div>
                                    <div>Required</div>
                                    <div>Priority</div>
                                    <div>Notes</div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label="Stage">Manager</div>
                                    <div class="workflow-table-cell" data-label="Required">
                                        <label class="workflow-checkbox-lock"><input type="checkbox" checked disabled aria-label="Manager required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label="Priority">
                                        <select name="managerPriority" class="workflow-priority-select" data-workflow-manager-priority>
                                            <option value="1" ${productWorkflowStartStage eq 'LOAN_OFFICER' ? '' : 'selected'}>1</option>
                                            <option value="2" ${productWorkflowStartStage eq 'LOAN_OFFICER' ? 'selected' : ''}>2</option>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label="Notes">Set this to 1 or 2 when both Manager and Loan Officer are part of the review path.</div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label="Stage">Loan Officer</div>
                                    <div class="workflow-table-cell" data-label="Required">
                                        <label class="workflow-checkbox-lock"><input name="loanOfficerReviewRequired" type="checkbox" value="true" ${(product.loanOfficerReviewRequired == true || (product.loanOfficerReviewRequired == null && settings.loanOfficerReviewRequired)) ? 'checked' : ''} data-workflow-loan-officer aria-label="Loan Officer required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label="Priority">
                                        <select name="loanOfficerPriority" class="workflow-priority-select" data-workflow-loan-officer-priority>
                                            <option value="1" ${productWorkflowStartStage eq 'LOAN_OFFICER' ? 'selected' : ''}>1</option>
                                            <option value="2" ${productWorkflowStartStage eq 'LOAN_OFFICER' ? '' : 'selected'}>2</option>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label="Notes">
                                        <c:choose>
                                            <c:when test="${product.loanOfficerReviewRequired == true || (product.loanOfficerReviewRequired == null && settings.loanOfficerReviewRequired)}">
                                                Set this to 1 or 2. It must not match the Manager priority.
                                            </c:when>
                                            <c:otherwise>
                                                Enable Loan Officer review in tenant approval flow settings to use this stage.
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label="Stage">Committee</div>
                                    <div class="workflow-table-cell" data-label="Required">
                                        <label class="workflow-checkbox-lock"><input name="committeeReviewRequired" type="checkbox" value="true" ${product.committeeReviewRequired ? 'checked' : ''} data-workflow-committee aria-label="Committee required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label="Priority">
                                        <select name="committeePriority" class="workflow-priority-select" data-workflow-committee-priority>
                                            <option value="3" ${product.resolvedCommitteePriority == 3 ? 'selected' : ''}>3</option>
                                            <option value="4" ${product.resolvedCommitteePriority == 4 ? 'selected' : ''}>4</option>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label="Notes">
                                        Optional stage used when this product must wait for committee decisions.
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label="Stage">Accountant</div>
                                    <div class="workflow-table-cell" data-label="Required">
                                        <label class="workflow-checkbox-lock"><input name="accountantReviewRequired" type="checkbox" value="true" ${product.accountantReviewRequired != false ? 'checked' : ''} data-workflow-accountant aria-label="Accountant required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label="Priority">
                                        <select name="accountantPriority" class="workflow-priority-select" data-workflow-accountant-priority>
                                            <option value="3" ${product.resolvedAccountantPriority == 3 ? 'selected' : ''}>3</option>
                                            <option value="4" ${product.resolvedAccountantPriority == 4 ? 'selected' : ''}>4</option>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label="Notes">
                                        Final financial verification before release.
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label="Stage">Disbursement Officer</div>
                                    <div class="workflow-table-cell" data-label="Required">
                                        <label class="workflow-checkbox-lock"><input type="checkbox" checked disabled aria-label="Disbursement Officer required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label="Priority">
                                        <select class="workflow-priority-select" disabled><option selected>5</option></select>
                                    </div>
                                    <div class="workflow-table-cell" data-label="Notes">
                                        Final release stage.
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title">Committee Rules</p>
                            <div class="workflow-support-grid">
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    Committee Reviewers Assigned
                                    <input name="committeeMinimumVotes" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.resolvedCommitteeMinimumVotes}" data-workflow-committee-votes />
                                </label>
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    Committee Approvals Needed
                                    <input name="committeeApprovalThreshold" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.resolvedCommitteeApprovalThreshold}" data-workflow-committee-threshold />
                                </label>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title">Eligibility Support</p>
                            <div class="workflow-support-grid">
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    Guarantors Required
                                    <input name="guarantorsRequired" type="number" min="0" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.guarantorsRequired}" data-workflow-guarantors />
                                </label>
                                <label class="flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    <input name="freshFinancialDataRequired" type="checkbox" value="true" ${product.freshFinancialDataRequired ? 'checked' : ''} data-workflow-loaded-financial />
                                    <span>Require Loaded Financial Data At Submission</span>
                                </label>
                                <label class="flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700 md:col-span-2">
                                    <input name="allowApplicationWithActiveLoan" type="checkbox" value="true" ${product.applicationWithActiveLoanAllowed ? 'checked' : ''} />
                                    <span>Allow Application With Active Loan</span>
                                </label>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title">Validation</p>
                            <div class="workflow-warning-stack mt-4" data-workflow-warnings></div>
                        </div>
                    </div>
                </section>

                <section class="product-builder-section">
                    <div class="product-builder-section-header">
                        <p class="erp-widget-title">Preview</p>
                        <h3 class="mt-1 text-lg font-bold text-sacco-ink">Resolved Flow</h3>
                        <p class="mt-1 text-sm text-slate-500">The runtime path below reflects what the system would execute with current tenant settings.</p>
                    </div>
                    <div class="product-builder-section-body">
                        <div class="workflow-preview-shell">
                            <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Approval Workflow</p>
                            <div class="mt-3 workflow-preview-runtime" data-workflow-preview-runtime></div>
                        </div>
                    </div>
                </section>

                <div class="app-modal-actions">
                    <button type="button"
                            class="app-btn btn-neutral"
                            data-product-modal-close="product-${product.id}">
                        Cancel
                    </button>
                    <button type="submit" class="app-btn btn-primary">Save</button>
                </div>
            </form>
            </div>
        </div>
    </div>
</c:forEach>

<script>
    (() => {
        const body = document.body;

        document.querySelectorAll('[data-product-modal]').forEach((modal) => {
            modal.style.position = 'fixed';
            modal.style.inset = '0';
            modal.style.zIndex = '90';
            document.body.appendChild(modal);
        });

        function workflowWarning(tone, message) {
            const block = document.createElement('div');
            block.className = 'workflow-warning is-' + tone;
            block.textContent = message;
            return block;
        }

        function numberOrNull(input) {
            if (!input) {
                return null;
            }
            const raw = typeof input.value === 'string' ? input.value.trim() : '';
            if (raw === '') {
                return null;
            }
            const value = Number(raw);
            return Number.isFinite(value) ? value : Number.NaN;
        }

        function addValidationError(errors, key, message) {
            if (!key || !message || errors.some((entry) => entry.key === key && entry.message === message)) {
                return;
            }
            errors.push({ key, message });
        }

        function resolveFieldTargets(form, key) {
            switch (key) {
                case 'workflowStartStage': {
                    const group = form.querySelector('[data-field-group="workflowStartStage"]');
                    return group ? [group] : [];
                }
                case 'managerPriority': {
                    const field = form.querySelector('[name="managerPriority"]');
                    return field ? [field] : [];
                }
                case 'loanOfficerPriority': {
                    const field = form.querySelector('[name="loanOfficerPriority"]');
                    return field ? [field] : [];
                }
                default: {
                    const field = form.querySelector('[name="' + key + '"]');
                    return field ? [field] : [];
                }
            }
        }

        function fieldErrorContainer(target) {
            if (!target) {
                return null;
            }
            if (target.hasAttribute && target.hasAttribute('data-field-group')) {
                return target;
            }
            return target.closest('.workflow-table-cell')
                || target.closest('.workflow-start-grid')
                || target.closest('.workflow-checkbox-lock')
                || target.closest('label')
                || target.parentElement
                || target;
        }

        function clearFieldErrors(form) {
            form.querySelectorAll('.field-error-text').forEach((node) => node.remove());
            form.querySelectorAll('.field-error-input').forEach((node) => {
                node.classList.remove('field-error-input');
                node.removeAttribute('aria-invalid');
            });
            form.querySelectorAll('.field-error-container').forEach((node) => {
                node.classList.remove('field-error-container');
            });
        }

        function applyFieldError(form, key, message) {
            const targets = resolveFieldTargets(form, key);
            if (!targets.length) {
                return;
            }
            targets.forEach((target, index) => {
                const container = fieldErrorContainer(target);
                if (!container) {
                    return;
                }
                if (target.matches && target.matches('input, select, textarea')) {
                    target.classList.add('field-error-input');
                    target.setAttribute('aria-invalid', 'true');
                }
                container.classList.add('field-error-container');
                if (index > 0 || container.querySelector('[data-field-error-for="' + key + '"]')) {
                    return;
                }
                const note = document.createElement('p');
                note.className = 'field-error-text';
                note.setAttribute('data-field-error-for', key);
                note.textContent = message;
                container.appendChild(note);
            });
        }

        function applyServerFieldErrors(form) {
            const errorNodes = form.querySelectorAll('[data-modal-field-error]');
            errorNodes.forEach((node) => {
                const key = node.getAttribute('data-modal-field-error');
                const message = node.textContent.trim();
                if (key && message) {
                    applyFieldError(form, key, message);
                }
            });
        }

        function numericValue(input, fallback) {
            if (!input) {
                return fallback;
            }
            const value = Number(input.value);
            return Number.isFinite(value) ? value : fallback;
        }

        function generateProductCode(name) {
            if (!name) {
                return '';
            }
            return name
                .toUpperCase()
                .replace(/[^A-Z0-9]+/g, '_')
                .replace(/^_+|_+$/g, '')
                .slice(0, 64);
        }

        function renderRuntimePath(container, labels) {
            container.innerHTML = '';
            labels.forEach((label, index) => {
                const chip = document.createElement('span');
                chip.className = 'workflow-preview-chip';
                chip.textContent = label;
                container.appendChild(chip);
                if (index < labels.length - 1) {
                    const arrow = document.createElement('span');
                    arrow.className = 'workflow-preview-arrow';
                    arrow.textContent = '->';
                    container.appendChild(arrow);
                }
            });
        }

        function syncWorkflowBuilder(form) {
            if (!form) {
                return;
            }

            const committeeToggle = form.querySelector('[data-workflow-committee]');
            const loanOfficerToggle = form.querySelector('[data-workflow-loan-officer]');
            const committeeVotes = form.querySelector('[data-workflow-committee-votes]');
            const committeeThreshold = form.querySelector('[data-workflow-committee-threshold]');
            const committeePriority = form.querySelector('[data-workflow-committee-priority]');
            const guarantorsInput = form.querySelector('[data-workflow-guarantors]');
            const loadedFinancialInput = form.querySelector('[data-workflow-loaded-financial]');
            const warningsRoot = form.querySelector('[data-workflow-warnings]');
            const runtimeRoot = form.querySelector('[data-workflow-preview-runtime]');
            const accountantToggle = form.querySelector('[data-workflow-accountant]');
            const accountantPriority = form.querySelector('[data-workflow-accountant-priority]');
            const managerPriority = form.querySelector('[data-workflow-manager-priority]');
            const loanOfficerPriority = form.querySelector('[data-workflow-loan-officer-priority]');
            const managerStartRadio = form.querySelector('[data-workflow-start-radio="manager"]');
            const loanOfficerStartRadio = form.querySelector('[data-workflow-start-radio="loanOfficer"]');
            const workflowStartStageField = form.querySelector('[data-workflow-start-stage-field]');
            const productNameField = form.querySelector('[data-product-name-field]');
            const productCodeField = form.querySelector('[data-product-code-field][data-product-code-generated="true"]');

            if (!committeeToggle || !warningsRoot || !runtimeRoot) {
                return;
            }

            const tenantLoanOfficerEnabled = form.dataset.tenantLoanOfficerEnabled === 'true';
            const tenantBoardEnabled = form.dataset.tenantBoardEnabled === 'true';
            const activeLoanOfficers = Number(form.dataset.activeLoanOfficers || '0');
            const activeBoardMembers = Number(form.dataset.activeBoardMembers || '0');
            const activeAccountants = Number(form.dataset.activeAccountants || '0');
            const activeDisbursementOfficers = Number(form.dataset.activeDisbursementOfficers || '0');

            function syncProductCode() {
                if (!productCodeField || !productNameField) {
                    return;
                }
                productCodeField.value = generateProductCode(productNameField.value);
            }

            function syncReviewPriorities(source) {
                if (!managerPriority || !loanOfficerPriority || !loanOfficerToggle) {
                    return;
                }
                if (!loanOfficerToggle.checked) {
                    managerPriority.value = '1';
                    loanOfficerPriority.value = '2';
                    loanOfficerPriority.disabled = true;
                    if (managerStartRadio) {
                        managerStartRadio.checked = true;
                    }
                    if (loanOfficerStartRadio) {
                        loanOfficerStartRadio.checked = false;
                        loanOfficerStartRadio.disabled = true;
                    }
                    if (workflowStartStageField) {
                        workflowStartStageField.value = 'MANAGER';
                    }
                    return;
                }

                loanOfficerPriority.disabled = false;
                if (loanOfficerStartRadio) {
                    loanOfficerStartRadio.disabled = false;
                }

                if (source === 'manager-radio' && managerStartRadio && managerStartRadio.checked) {
                    managerPriority.value = '1';
                    loanOfficerPriority.value = '2';
                } else if (source === 'loan-officer-radio' && loanOfficerStartRadio && loanOfficerStartRadio.checked) {
                    managerPriority.value = '2';
                    loanOfficerPriority.value = '1';
                }

                const prioritiesAreDistinct = managerPriority.value !== loanOfficerPriority.value;
                if (prioritiesAreDistinct && managerStartRadio && loanOfficerStartRadio) {
                    managerStartRadio.checked = managerPriority.value === '1';
                    loanOfficerStartRadio.checked = loanOfficerPriority.value === '1';
                }
                if (workflowStartStageField) {
                    if (prioritiesAreDistinct) {
                        workflowStartStageField.value = loanOfficerPriority.value === '1' ? 'LOAN_OFFICER' : 'MANAGER';
                    } else {
                        workflowStartStageField.value = loanOfficerStartRadio && loanOfficerStartRadio.checked ? 'LOAN_OFFICER' : 'MANAGER';
                    }
                }
            }

            function syncSavedWorkflowStartStage() {
                if (!workflowStartStageField || !managerPriority || !loanOfficerPriority || !loanOfficerToggle || !loanOfficerToggle.checked) {
                    return;
                }
                if (workflowStartStageField.value === 'LOAN_OFFICER') {
                    managerPriority.value = '2';
                    loanOfficerPriority.value = '1';
                } else if (workflowStartStageField.value === 'MANAGER') {
                    managerPriority.value = '1';
                    loanOfficerPriority.value = '2';
                }
            }

            function applyRules() {
                const loanOfficerEnabled = loanOfficerToggle && loanOfficerToggle.checked;
                const committeeEnabled = committeeToggle.checked;
                const accountantEnabled = accountantToggle && accountantToggle.checked;
                const guarantors = Math.max(0, numericValue(guarantorsInput, 0));
                const minimumVotes = Math.max(1, numericValue(committeeVotes, 1));
                const approvalThreshold = Math.max(1, numericValue(committeeThreshold, 1));
                const loadedFinancialRequired = loadedFinancialInput && loadedFinancialInput.checked;

                syncProductCode();
                syncReviewPriorities();

                const managerPriorityValue = Math.max(1, Math.min(2, numericValue(managerPriority, 1)));
                const loanOfficerPriorityValue = Math.max(1, Math.min(2, numericValue(loanOfficerPriority, 2)));
                const committeePriorityValue = Math.max(3, Math.min(4, numericValue(committeePriority, 3)));
                const accountantPriorityValue = Math.max(3, Math.min(4, numericValue(accountantPriority, 4)));
                if (committeePriority) {
                    committeePriority.value = String(committeePriorityValue);
                    committeePriority.disabled = !committeeEnabled;
                }
                if (accountantPriority) {
                    accountantPriority.value = String(accountantPriorityValue);
                    accountantPriority.disabled = !accountantEnabled;
                }
                if (committeeVotes) {
                    committeeVotes.disabled = !committeeEnabled;
                }
                if (committeeThreshold) {
                    committeeThreshold.disabled = !committeeEnabled;
                }

                const warnings = [];
                if (loanOfficerEnabled && activeLoanOfficers <= 0) {
                    warnings.push(workflowWarning('danger', 'Loan Officer review is enabled for this product, but there are no active Loan Officers assigned yet.'));
                }
                if (loanOfficerEnabled && managerPriorityValue === loanOfficerPriorityValue) {
                    warnings.push(workflowWarning('danger', 'Manager and Loan Officer cannot share the same priority. One must be 1 and the other 2.'));
                }

                if (committeeEnabled && activeBoardMembers <= 0) {
                    warnings.push(workflowWarning('danger', 'Committee review is configured, but there are no active board members available for assignment.'));
                }
                if (committeeEnabled && minimumVotes > activeBoardMembers && activeBoardMembers > 0) {
                    warnings.push(workflowWarning('danger', 'Committee minimum votes are higher than the active board member count.'));
                }
                if (committeeEnabled && approvalThreshold > minimumVotes) {
                    warnings.push(workflowWarning('danger', 'Committee approval threshold cannot be greater than committee minimum votes.'));
                }
                if (committeeEnabled && accountantEnabled && committeePriorityValue === accountantPriorityValue) {
                    warnings.push(workflowWarning('danger', 'Committee and Accountant cannot share the same priority slot.'));
                }
                if (accountantEnabled && activeAccountants <= 0) {
                    warnings.push(workflowWarning('danger', 'Accountant review is enabled for this product, but there are no active accountants available.'));
                }
                if (activeDisbursementOfficers <= 0) {
                    warnings.push(workflowWarning('danger', 'No active disbursement officers are available, so runtime cannot release approved loans.'));
                }
                if (loadedFinancialRequired) {
                    warnings.push(workflowWarning('info', 'Members must load current financial data before this product can move into review.'));
                }
                if (guarantors > 0) {
                    warnings.push(workflowWarning('info', 'This product waits for ' + guarantors + ' guarantor approval' + (guarantors === 1 ? '' : 's') + ' before the review flow starts.'));
                }

                warningsRoot.innerHTML = '';
                warnings.forEach((warning) => warningsRoot.appendChild(warning));

                const runtimeLabels = ['Member'];
                if (loadedFinancialRequired) {
                    runtimeLabels.push('Loaded Financial Data');
                }
                if (guarantors > 0) {
                    runtimeLabels.push(guarantors + ' Guarantor' + (guarantors === 1 ? '' : 's'));
                }
                if (loanOfficerEnabled) {
                    if (managerPriorityValue === loanOfficerPriorityValue) {
                        runtimeLabels.push('Fix Manager / Loan Officer Priority');
                    } else {
                        runtimeLabels.push(managerPriorityValue === 1 ? 'Manager' : 'Loan Officer');
                        runtimeLabels.push(managerPriorityValue === 1 ? 'Loan Officer' : 'Manager');
                    }
                } else {
                    runtimeLabels.push('Manager');
                }
                if (committeeEnabled) {
                    runtimeLabels.push('Committee');
                }
                if (accountantEnabled) {
                    runtimeLabels.push('Accountant');
                }
                runtimeLabels.push('Disbursement');
                renderRuntimePath(runtimeRoot, runtimeLabels);
            }

            function validateBeforeSubmit() {
                const errors = [];
                const displayOrder = numberOrNull(form.querySelector('[name="displayOrder"]'));
                const productName = form.querySelector('[name="productName"]');
                const productDescription = form.querySelector('[name="productDescription"]');
                const minimumAmount = numberOrNull(form.querySelector('[name="minimumAmount"]'));
                const maximumAmount = numberOrNull(form.querySelector('[name="maximumAmount"]'));
                const savingsPercent = numberOrNull(form.querySelector('[name="maxLoanSavingsPercent"]'));
                const insurancePercent = numberOrNull(form.querySelector('[name="insurancePercent"]'));
                const annualInterestPercent = numberOrNull(form.querySelector('[name="annualInterestPercent"]'));
                const minRepaymentMonths = numberOrNull(form.querySelector('[name="minRepaymentMonths"]'));
                const maxRepaymentMonths = numberOrNull(form.querySelector('[name="maxRepaymentMonths"]'));
                const guarantorsRequired = numberOrNull(form.querySelector('[name="guarantorsRequired"]'));
                const committeeVotesValue = numberOrNull(committeeVotes);
                const committeeThresholdValue = numberOrNull(committeeThreshold);
                const managerPriorityValue = numberOrNull(managerPriority);
                const loanOfficerPriorityValue = numberOrNull(loanOfficerPriority);
                const committeePriorityValue = numberOrNull(committeePriority);
                const accountantPriorityValue = numberOrNull(accountantPriority);
                const loanOfficerEnabled = loanOfficerToggle && loanOfficerToggle.checked;
                const committeeEnabled = committeeToggle && committeeToggle.checked;
                const accountantEnabled = accountantToggle && accountantToggle.checked;

                if (displayOrder === null || Number.isNaN(displayOrder) || displayOrder < 1) {
                    addValidationError(errors, 'displayOrder', 'Display order must be 1 or higher.');
                }
                if (productName && !productName.value.trim()) {
                    addValidationError(errors, 'productName', 'Enter a loan product name.');
                } else if (productName && productName.value.trim().length > 120) {
                    addValidationError(errors, 'productName', 'Loan product name must be 120 characters or fewer.');
                }
                if (productDescription && productDescription.value.length > 500) {
                    addValidationError(errors, 'productDescription', 'Keep the description within 500 characters.');
                }
                if (minimumAmount === null || Number.isNaN(minimumAmount) || minimumAmount < 0) {
                    addValidationError(errors, 'minimumAmount', 'Minimum amount cannot be negative.');
                }
                if (maximumAmount !== null) {
                    if (Number.isNaN(maximumAmount) || maximumAmount <= 0) {
                        addValidationError(errors, 'maximumAmount', 'Maximum amount must be greater than zero.');
                    } else if (minimumAmount !== null && !Number.isNaN(minimumAmount) && maximumAmount < minimumAmount) {
                        addValidationError(errors, 'maximumAmount', 'Maximum amount cannot be lower than the minimum amount.');
                    }
                }
                if (savingsPercent === null || Number.isNaN(savingsPercent) || savingsPercent <= 0) {
                    addValidationError(errors, 'maxLoanSavingsPercent', 'Savings percentage must be greater than zero.');
                }
                if (insurancePercent === null || Number.isNaN(insurancePercent) || insurancePercent < 0) {
                    addValidationError(errors, 'insurancePercent', 'Insurance percentage cannot be negative.');
                }
                if (annualInterestPercent === null || Number.isNaN(annualInterestPercent) || annualInterestPercent < 0) {
                    addValidationError(errors, 'annualInterestPercent', 'Annual interest percentage cannot be negative.');
                }
                if (minRepaymentMonths === null || Number.isNaN(minRepaymentMonths) || minRepaymentMonths < 1) {
                    addValidationError(errors, 'minRepaymentMonths', 'Minimum repayment period must be at least 1 month.');
                }
                if (maxRepaymentMonths === null || Number.isNaN(maxRepaymentMonths) || maxRepaymentMonths < 1) {
                    addValidationError(errors, 'maxRepaymentMonths', 'Maximum repayment period must be at least 1 month.');
                } else if (minRepaymentMonths !== null && !Number.isNaN(minRepaymentMonths) && maxRepaymentMonths < minRepaymentMonths) {
                    addValidationError(errors, 'maxRepaymentMonths', 'Maximum repayment period cannot be lower than the minimum repayment period.');
                }
                if (guarantorsRequired === null || Number.isNaN(guarantorsRequired) || guarantorsRequired < 0) {
                    addValidationError(errors, 'guarantorsRequired', 'Guarantors required cannot be negative.');
                }

                if (loanOfficerEnabled) {
                    if (managerPriorityValue === null || Number.isNaN(managerPriorityValue) || managerPriorityValue < 1 || managerPriorityValue > 2) {
                        addValidationError(errors, 'managerPriority', 'Manager priority must be 1 or 2.');
                    }
                    if (loanOfficerPriorityValue === null || Number.isNaN(loanOfficerPriorityValue) || loanOfficerPriorityValue < 1 || loanOfficerPriorityValue > 2) {
                        addValidationError(errors, 'loanOfficerPriority', 'Loan Officer priority must be 1 or 2.');
                    }
                    if (!Number.isNaN(managerPriorityValue) && !Number.isNaN(loanOfficerPriorityValue) && managerPriorityValue === loanOfficerPriorityValue) {
                        addValidationError(errors, 'managerPriority', 'Choose different priorities for Manager and Loan Officer. One must be 1 and the other 2.');
                        addValidationError(errors, 'loanOfficerPriority', 'Choose different priorities for Manager and Loan Officer. One must be 1 and the other 2.');
                    }
                    if (activeLoanOfficers <= 0) {
                        addValidationError(errors, 'loanOfficerReviewRequired', 'Assign at least one active Loan Officer before using this stage.');
                    }
                }

                if (committeeEnabled) {
                    if (activeBoardMembers <= 0) {
                        addValidationError(errors, 'committeeReviewRequired', 'Assign at least one active committee reviewer before using this stage.');
                    }
                    if (committeeVotesValue === null || Number.isNaN(committeeVotesValue) || committeeVotesValue < 1) {
                        addValidationError(errors, 'committeeMinimumVotes', 'Committee reviewers assigned must be at least 1.');
                    }
                    if (committeeThresholdValue === null || Number.isNaN(committeeThresholdValue) || committeeThresholdValue < 1) {
                        addValidationError(errors, 'committeeApprovalThreshold', 'Committee approvals needed must be at least 1.');
                    }
                    if (!Number.isNaN(committeeVotesValue) && !Number.isNaN(committeeThresholdValue) && committeeThresholdValue > committeeVotesValue) {
                        addValidationError(errors, 'committeeMinimumVotes', 'Committee approvals needed cannot be greater than committee reviewers assigned.');
                        addValidationError(errors, 'committeeApprovalThreshold', 'Committee approvals needed cannot be greater than committee reviewers assigned.');
                    }
                    if (activeBoardMembers > 0 && !Number.isNaN(committeeVotesValue) && committeeVotesValue > activeBoardMembers) {
                        addValidationError(errors, 'committeeMinimumVotes', 'Committee reviewers assigned cannot exceed the active board member count.');
                    }
                    if (activeBoardMembers > 0 && !Number.isNaN(committeeThresholdValue) && committeeThresholdValue > activeBoardMembers) {
                        addValidationError(errors, 'committeeApprovalThreshold', 'Committee approvals needed cannot exceed the active board member count.');
                    }
                }

                if (committeeEnabled && accountantEnabled
                    && !Number.isNaN(committeePriorityValue)
                    && !Number.isNaN(accountantPriorityValue)
                    && committeePriorityValue === accountantPriorityValue) {
                    addValidationError(errors, 'committeePriority', 'Committee and Accountant cannot share the same priority slot.');
                    addValidationError(errors, 'accountantPriority', 'Committee and Accountant cannot share the same priority slot.');
                }

                if (accountantEnabled && activeAccountants <= 0) {
                    addValidationError(errors, 'accountantReviewRequired', 'Assign at least one active Accountant before using this stage.');
                }

                return errors;
            }

            [loanOfficerToggle, committeeToggle, committeeVotes, committeeThreshold, committeePriority, accountantToggle, accountantPriority, guarantorsInput, loadedFinancialInput]
                .filter(Boolean)
                .forEach((field) => {
                    field.addEventListener('change', applyRules);
                    field.addEventListener('input', applyRules);
                });

            [managerStartRadio, loanOfficerStartRadio].filter(Boolean).forEach((field) => {
                field.addEventListener('change', () => {
                    syncReviewPriorities(field.dataset.workflowStartRadio === 'loanOfficer' ? 'loan-officer-radio' : 'manager-radio');
                    applyRules();
                });
            });

            [managerPriority, loanOfficerPriority].filter(Boolean).forEach((field) => {
                field.addEventListener('change', () => {
                    syncReviewPriorities(field.hasAttribute('data-workflow-loan-officer-priority') ? 'loan-officer-select' : 'manager-select');
                    applyRules();
                });
            });

            if (productNameField && productCodeField) {
                productNameField.addEventListener('input', syncProductCode);
                productNameField.addEventListener('change', syncProductCode);
                syncProductCode();
            }

            form.addEventListener('submit', (event) => {
                clearFieldErrors(form);
                applyRules();
                const errors = validateBeforeSubmit();
                if (!errors.length) {
                    return;
                }
                event.preventDefault();
                errors.forEach((entry) => applyFieldError(form, entry.key, entry.message));
                const firstTarget = resolveFieldTargets(form, errors[0].key)[0];
                if (firstTarget && typeof firstTarget.scrollIntoView === 'function') {
                    firstTarget.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
                if (firstTarget && typeof firstTarget.focus === 'function') {
                    firstTarget.focus({ preventScroll: true });
                }
            });

            syncSavedWorkflowStartStage();
            applyRules();
            applyServerFieldErrors(form);
        }

        document.querySelectorAll('[data-product-workflow-builder="true"]').forEach((form) => {
            syncWorkflowBuilder(form);
        });

        document.querySelectorAll('[data-inline-validation-form="loan-settings"]:not([data-product-workflow-builder="true"])').forEach((form) => {
            applyServerFieldErrors(form);
            form.addEventListener('submit', (event) => {
                clearFieldErrors(form);
                const applicationFee = numberOrNull(form.querySelector('[name="applicationFee"]'));
                const errors = [];
                if (applicationFee === null || Number.isNaN(applicationFee) || applicationFee < 0) {
                    addValidationError(errors, 'applicationFee', 'Application fee cannot be negative.');
                }
                if (!errors.length) {
                    return;
                }
                event.preventDefault();
                errors.forEach((entry) => applyFieldError(form, entry.key, entry.message));
                const firstTarget = resolveFieldTargets(form, errors[0].key)[0];
                if (firstTarget && typeof firstTarget.focus === 'function') {
                    firstTarget.focus();
                }
            });
        });

        function closeAllProductModals() {
            document.querySelectorAll('[data-product-modal]').forEach((modal) => {
                modal.classList.add('hidden');
                modal.classList.remove('is-open');
            });
            body.classList.remove('overflow-hidden');
        }

        document.querySelectorAll('[data-product-modal-open]').forEach((button) => {
            button.addEventListener('click', () => {
                const key = button.getAttribute('data-product-modal-open');
                closeAllProductModals();
                const modal = document.querySelector('[data-product-modal="' + key + '"]');
                if (modal) {
                    modal.classList.remove('hidden');
                    modal.classList.add('is-open');
                    body.classList.add('overflow-hidden');
                }
            });
        });

        document.querySelectorAll('[data-product-modal-close]').forEach((button) => {
            button.addEventListener('click', closeAllProductModals);
        });

        document.querySelectorAll('[data-product-modal]').forEach((modal) => {
            modal.addEventListener('click', (event) => {
                if (event.target === modal) {
                    closeAllProductModals();
                }
            });
        });

        const initialModalTarget = document.querySelector('[data-open-product-modal]');
        if (initialModalTarget) {
            const key = initialModalTarget.getAttribute('data-open-product-modal');
            const modal = key ? document.querySelector('[data-product-modal="' + key + '"]') : null;
            if (modal) {
                closeAllProductModals();
                modal.classList.remove('hidden');
                modal.classList.add('is-open');
                body.classList.add('overflow-hidden');
                const modalScroll = modal.querySelector('.app-modal-scroll');
                if (modalScroll) {
                    modalScroll.scrollTop = 0;
                }
            }
        }
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
