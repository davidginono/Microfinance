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
        display: grid;
        gap: 0.75rem;
        max-width: 34rem;
    }
    .savings-ratio-mode-toggle {
        display: inline-flex;
        width: fit-content;
        overflow: hidden;
        border: 1px solid #cbd5e1;
        border-radius: 0.375rem;
        background: #ffffff;
    }
    .savings-ratio-mode-toggle button {
        min-height: 2.4rem;
        border: 0;
        background: transparent;
        padding: 0 0.9rem;
        color: #475569;
        font-size: 0.78rem;
        font-weight: 700;
        letter-spacing: 0.04em;
        text-transform: uppercase;
    }
    .savings-ratio-mode-toggle button.is-active {
        background: #e0f2fe;
        color: #075985;
        box-shadow: inset 0 0 0 1px rgba(14, 116, 144, 0.12);
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
    .savings-fraction-control {
        display: inline-grid;
        width: fit-content;
        grid-template-columns: 6.5rem auto 6.5rem;
        gap: 0.35rem;
        align-items: center;
    }
    .savings-fraction-control input {
        min-height: 3rem !important;
        padding-inline: 0.75rem;
    }
    .savings-multiplier-control.hidden,
    .savings-fraction-control.hidden {
        display: none !important;
    }
    .savings-multiplier-label,
    .savings-fraction-divider {
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
    <p class="erp-breadcrumb"><spring:message code="admin.settings.breadcrumb" text="Admin Tools / Settings & Controls" /></p>
    <h1 class="erp-page-title"><spring:message code="admin.settings.title" text="Settings & Controls" /></h1>
    <p class="erp-page-subtitle"><spring:message code="admin.settings.subtitle" text="Manage loan, guarantor, and board controls." /></p>
</div>

<c:if test="${not empty openProductModalKey}">
    <div hidden data-open-product-modal="${fn:escapeXml(openProductModalKey)}"></div>
</c:if>

<c:if test="${settingsSection eq 'language'}">
    <section class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
            <p class="erp-widget-title"><spring:message code="admin.settings.language.eyebrow" text="Language Settings" /></p>
            <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="admin.settings.language.title" text="Workspace Default Language" /></h2>
            <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.language.subtitle" text="Sets the default language used by SACCO workflow screens that follow the workspace setting." /></p>
        </div>
        <form action="/admin/settings-controls/language" method="post" class="erp-panel-body">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div class="settings-action-bar">
                <label class="settings-action-note block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <spring:message code="admin.settings.language.label" text="Default language" />
                    <select name="defaultLanguage" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800">
                        <option value="en" ${settings.defaultLanguage ne 'sw' ? 'selected' : ''}><spring:message code="admin.settings.language.english" text="English" /></option>
                        <option value="sw" ${settings.defaultLanguage eq 'sw' ? 'selected' : ''}><spring:message code="admin.settings.language.swahili" text="Kiswahili" /></option>
                    </select>
                </label>
                <button type="submit" class="settings-action-button app-btn btn-primary"><spring:message code="admin.settings.language.save" text="Save Language" /></button>
            </div>
        </form>
    </section>
</c:if>

<c:if test="${settingsSection eq 'otp'}">
    <section class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
            <p class="erp-widget-title">Authentication</p>
            <h2 class="mt-1 text-xl font-bold text-sacco-ink">Station OTP Delivery</h2>
            <p class="mt-1 text-sm text-slate-500">Choose how this station sends authentication and workflow confirmation codes.</p>
        </div>
        <form action="/admin/settings-controls/otp-delivery" method="post" class="erp-panel-body">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div class="grid gap-3 lg:grid-cols-3">
                <label class="settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                    <input name="otpDeliveryChannel" type="radio" value="EMAIL" class="mt-1" ${stationOtpDeliveryChannel eq 'EMAIL' ? 'checked' : ''} />
                    <span>
                        <span class="block font-semibold text-slate-900">Email</span>
                        <span class="mt-1 block text-xs text-slate-500">Always send OTP codes to the account email.</span>
                    </span>
                </label>
                <label class="settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                    <input name="otpDeliveryChannel" type="radio" value="SMS" class="mt-1" ${stationOtpDeliveryChannel eq 'SMS' ? 'checked' : ''} />
                    <span>
                        <span class="block font-semibold text-slate-900">SMS only</span>
                        <span class="mt-1 block text-xs text-slate-500">Use normal station SMS units. OTP requests stop when units are depleted.</span>
                    </span>
                </label>
                <label class="settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                    <input name="otpDeliveryChannel" type="radio" value="SMS_WITH_EMAIL_FALLBACK" class="mt-1" ${stationOtpDeliveryChannel eq 'SMS_WITH_EMAIL_FALLBACK' ? 'checked' : ''} />
                    <span>
                        <span class="block font-semibold text-slate-900">SMS with email fallback</span>
                        <span class="mt-1 block text-xs text-slate-500">Try SMS first, then use email when SMS cannot be sent.</span>
                    </span>
                </label>
            </div>
            <div class="relative mt-5 flex flex-row items-center justify-between gap-2 flex-wrap">
                <p class="text-sm text-slate-500">The three reserved SMS alert units are never used for OTP codes.</p>
                <button type="submit" class="app-btn btn-primary">Save OTP Delivery</button>
            </div>
        </form>
    </section>
</c:if>

<c:if test="${settingsSection eq 'board'}">
    <div class="erp-panel mb-4">
        <div class="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
                <p class="erp-widget-title">Approval Flow</p>
                <h2 class="mt-1 text-xl font-bold text-sacco-ink">Tenant Approval Configuration</h2>
                <p class="mt-1 text-sm text-slate-500">Product workflows decide the review path. Final release remains manual and claim-protected.</p>
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
                        <span class="block font-semibold text-slate-900">Require Board Committee Review</span>
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
                        Used only when Board Committee review is enabled.
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
                    <p class="mt-3 text-xs text-slate-500">Examples: Branch Manager to Accountant to Disbursement, or Loan Officer to Board Committee to Accountant to Disbursement.</p>
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
                    Active Disbursement Claim Holders: <strong>${activeDisbursementClaimHolderCount}</strong>
                </div>
            </div>

            <div class="app-modal-actions !justify-start md:justify-end">
                <button type="submit" class="app-btn btn-primary">Save</button>
            </div>
        </form>
    </div>
</c:if>

<c:if test="${settingsSection eq 'guarantor'}">
    <section class="erp-panel overflow-hidden mb-4">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
            <p class="erp-widget-title">Qualification Policies</p>
            <h2 class="mt-1 text-xl font-bold text-sacco-ink">Applicant And Guarantor Controls</h2>
        </div>
        <form action="/admin/settings-controls/qualification-policies" method="post" class="erp-panel-body grid gap-5">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div class="settings-action-bar settings-action-bar--split">
                <p class="settings-action-note max-w-3xl text-sm text-slate-500">These are general applicant and guarantor rules for this station. Product-specific guarantor savings are configured inside each loan product.</p>
                <button type="submit" class="settings-action-button app-btn btn-primary">Save configuration</button>
            </div>
            <div class="grid gap-4 lg:grid-cols-2">
                <div class="rounded-md border border-slate-200 bg-white p-4">
                    <p class="erp-widget-title">Loan Applicants</p>
                    <div class="mt-4 grid gap-4 md:grid-cols-2">
                        <label class="settings-checkbox-card flex items-center gap-3 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                            <input name="applicantMaxDefaultedLoans" type="checkbox" value="1" ${not empty policyApplicantMaxDefaultedLoans and policyApplicantMaxDefaultedLoans gt 0 ? 'checked' : ''} />
                            <span>
                                <span class="block font-semibold text-slate-900">Block applicants with defaulted loans</span>
                                <span class="mt-1 block text-xs text-slate-500">If checked, one defaulted loan blocks new applications.</span>
                            </span>
                        </label>
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Forfeited Application Waiting Period (Days)
                            <input name="applicantForfeitedWaitDays" type="number" min="0" max="15" data-number-range-max="15" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${policyApplicantForfeitedWaitDays}" />
                            <input name="applicantMaxForfeitedLoans" type="hidden" value="${policyApplicantMaxForfeitedLoans}" />
                            <input name="applicantForfeitedLookbackDays" type="hidden" value="${policyApplicantForfeitedLookbackDays}" />
                        </label>
                    </div>
                </div>
                <div class="rounded-md border border-slate-200 bg-white p-4">
                    <p class="erp-widget-title">Guarantors</p>
                    <div class="mt-4 grid gap-4 md:grid-cols-2">
                        <label class="settings-checkbox-card flex items-center gap-3 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                            <input name="guarantorWithActiveLoanAllowed" type="checkbox" value="true" ${policyGuarantorWithActiveLoanAllowed ? 'checked' : ''} />
                            <span>
                                <span class="block font-semibold text-slate-900">Allow guarantors with active loans</span>
                                <span class="mt-1 block text-xs text-slate-500">If unchecked, members with active loans cannot guarantee.</span>
                            </span>
                        </label>
                        <fmt:formatNumber value="${policyGuarantorMaxGuaranteedLoanAmount}" maxFractionDigits="0" groupingUsed="false" var="policyGuarantorMaxGuarantees" />
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            Max Guarantees
                            <input name="guarantorMaxGuaranteedLoanAmount" type="number" min="0" max="15" step="1" data-number-range-max="15" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${policyGuarantorMaxGuarantees}" />
                        </label>
                        <label class="settings-checkbox-card flex items-center gap-3 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                            <input name="guarantorMaxDefaultedLoans" type="checkbox" value="1" ${not empty policyGuarantorMaxDefaultedLoans and policyGuarantorMaxDefaultedLoans gt 0 ? 'checked' : ''} />
                            <span>
                                <span class="block font-semibold text-slate-900">Block guarantors with defaulted loans</span>
                                <span class="mt-1 block text-xs text-slate-500">If checked, one defaulted loan blocks guarantee approvals.</span>
                            </span>
                        </label>
                    </div>
                </div>
            </div>
        </form>
    </section>

</c:if>

<c:if test="${settingsSection eq 'loan'}">
    <div class="mb-4 flex flex-wrap justify-end gap-3">
        <button type="button"
                class="app-btn btn-neutral"
                data-product-modal-open="application-fee">
            <spring:message code="admin.settings.loanProducts.editApplicationFee" text="Edit application fee" />
        </button>
        <c:if test="${not empty loanProductsVersions}">
            <button type="button"
                    class="app-btn btn-neutral"
                    data-product-modal-open="loan-products-versions">
                <spring:message code="admin.settings.loanProducts.versions" text="Loan Products Versions" />
            </button>
        </c:if>
        <c:if test="${not customizedProductExists}">
            <button type="button"
                    class="app-btn btn-primary"
                    data-product-modal-open="create-product">
                <spring:message code="admin.settings.loanProducts.add" text="Add loan product" />
            </button>
        </c:if>
    </div>

    <div class="grid gap-4 xl:grid-cols-2 2xl:grid-cols-3">
        <c:forEach items="${products}" var="product">
            <c:set var="productManagerEnabled" value="${product.managerReviewRequired != false}" />
            <c:set var="productLoanOfficerEnabled"
                   value="${product.loanOfficerReviewRequired == true || (product.loanOfficerReviewRequired == null && settings.loanOfficerReviewRequired)}" />
            <c:set var="productWorkflowStartStage"><c:out value="${product.resolvedWorkflowStartStage}" /></c:set>
            <section class="erp-panel overflow-hidden">
                <div class="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                        <p class="erp-widget-title"><spring:message code="admin.settings.loanProducts.single" text="Loan Product" /></p>
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
                            <spring:message code="admin.settings.loanProducts.versionsShort" text="Versions" />
                        </button>
                        <button type="button"
                                class="app-btn btn-primary"
                                data-product-modal-open="product-${product.id}">
                            <spring:message code="common.edit" text="Edit" />
                        </button>
                    </div>
                </div>

                <div class="divide-y divide-slate-200">
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
                            <fmt:formatNumber value="${product.minimumAmount}" minFractionDigits="2" maxFractionDigits="2" />
                            <spring:message code="common.to" text="to" />
                            <c:choose>
                                <c:when test="${product.maximumAmount ne null}">
                                    <fmt:formatNumber value="${product.maximumAmount}" minFractionDigits="2" maxFractionDigits="2" />
                                </c:when>
                                <c:otherwise><spring:message code="common.notSet" text="Not set" /></c:otherwise>
                            </c:choose>
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.loanProducts.savingsMultiple" text="Loan Amount Limit By Savings" /></p>
                            <p class="text-sm text-slate-500">Maximum loan multiple allowed against member savings.</p>
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
                            <span class="block text-sm font-medium text-slate-600"><fmt:formatNumber value="${product.interestRate * 100}" minFractionDigits="2" maxFractionDigits="2" />%</span>
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
                            <p class="text-sm font-semibold text-sacco-ink">Guarantor Savings Check</p>
                            <p class="text-sm text-slate-500">Controls whether selected guarantors must meet this product's minimum savings.</p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">
                            <c:choose>
                                <c:when test="${product.guarantorMinSavingsCheckRequired}">Enabled</c:when>
                                <c:otherwise>Disabled</c:otherwise>
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
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.loanProducts.loadedFinancialData" text="Loaded Financial Data" /></p>
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
                            <span class="block text-sm font-semibold text-emerald-700" style="order: ${productManagerEnabled ? product.resolvedManagerPriority : 6};">
                                <c:choose>
                                    <c:when test="${productManagerEnabled}">P${product.resolvedManagerPriority} Manager</c:when>
                                    <c:otherwise>Manager Skipped</c:otherwise>
                                </c:choose>
                            </span>
                            <span class="mt-1 block text-sm font-semibold ${(productLoanOfficerEnabled or productManagerEnabled) ? 'text-emerald-700' : 'text-slate-500'}" style="order: ${productLoanOfficerEnabled ? product.resolvedLoanOfficerPriority : 6};">
                                <c:choose>
                                    <c:when test="${productLoanOfficerEnabled}">P${product.resolvedLoanOfficerPriority} Loan Officer</c:when>
                                    <c:otherwise>Loan Officer Skipped</c:otherwise>
                                </c:choose>
                            </span>
                            <span class="mt-1 block text-sm font-semibold ${product.committeeReviewRequired ? 'text-emerald-700' : 'text-slate-500'}" style="order: ${product.committeeReviewRequired ? product.resolvedCommitteePriority : 6};">
                                P${product.resolvedCommitteePriority} Committee ${product.committeeReviewRequired ? 'Configured' : 'Skipped'}
                            </span>
                            <span class="mt-1 block text-sm font-semibold ${product.accountantReviewRequired != false ? 'text-emerald-700' : 'text-slate-500'}" style="order: ${product.accountantReviewRequired != false ? product.resolvedAccountantPriority : 6};">
                                P${product.resolvedAccountantPriority} Accountant ${product.accountantReviewRequired != false ? 'Configured' : 'Skipped'}
                            </span>
                            <span class="mt-1 block text-sm font-semibold ${product.disbursementOfficerRequired != false ? 'text-emerald-700' : 'text-slate-500'}" style="order: 7;">
                                P5 ${product.disbursementOfficerRequired != false ? 'Disbursement Officer Required' : 'Disbursement Claim Release'}
                            </span>
                        </div>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Disbursement Proof</p>
                            <p class="text-sm text-slate-500">Controls whether a proof attachment is required before releasing this product.</p>
                        </div>
                        <p class="text-right text-base font-semibold ${product.disbursementProofRequired != false ? 'text-emerald-700' : 'text-slate-600'}">
                            ${product.disbursementProofRequired != false ? 'Required' : 'Optional'}
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Applicant Attachments</p>
                            <p class="text-sm text-slate-500">Controls whether members must upload a supporting attachment while applying.</p>
                        </div>
                        <p class="text-right text-base font-semibold ${product.applicantAttachmentRequired ? 'text-emerald-700' : 'text-slate-600'}">
                            ${product.applicantAttachmentRequired ? 'Required' : 'Optional'}
                        </p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink"><spring:message code="admin.settings.workflow.committeeRules" text="Board Committee" /></p>
                            <p class="text-sm text-slate-500"><spring:message code="admin.settings.workflow.committeeRulesHelp" text="Minimum votes assigned and approval threshold used for committee decisions." /></p>
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
                            <spring:message code="common.close" text="Close" />
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
                      data-active-disbursement-claim-holders="${activeDisbursementClaimHolderCount}"
                      data-tenant-loan-officer-enabled="${settings.loanOfficerReviewRequired}"
                      data-tenant-board-enabled="${settings.boardReviewRequired}">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="modalKey" value="create-product" />
                    <input type="hidden" name="productCode" value="" data-product-code-field data-product-code-generated="true" />
                    <input type="hidden" name="workflowStartStage" value="MANAGER" data-workflow-start-stage-field />

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
                            <p class="erp-widget-title"><spring:message code="admin.settings.loanProducts.basics" text="Basics" /></p>
                            <h3 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="admin.settings.loanProducts.identity" text="Product Identity" /></h3>
                            <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.settings.loanProducts.identityCreateHelp" text="Set the member-facing name, display order, and status for this product." /></p>
                        </div>
                        <div class="product-builder-section-body product-identity-grid">
                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.displayOrder" text="Display Order" />
                                <input name="displayOrder" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="5" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.name" text="Loan Product Name" />
                                <input name="productName" type="text" required maxlength="120" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder='<spring:message code="admin.settings.loanProducts.namePlaceholder" text="e.g. School Fees Booster" />' data-product-name-field />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.status" text="Product Status" />
                                <select name="productStatus" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                    <option value="DRAFT"><spring:message code="status.draft" text="Draft" /></option>
                                    <option value="ACTIVE" selected><spring:message code="status.active" text="Active" /></option>
                                    <option value="SUSPENDED"><spring:message code="status.suspended" text="Suspended" /></option>
                                    <option value="RETIRED"><spring:message code="status.retired" text="Retired" /></option>
                                </select>
                            </label>

                            <label class="product-identity-description block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="common.description" text="Description" />
                                <textarea name="productDescription" rows="3" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder='<spring:message code="admin.settings.loanProducts.descriptionPlaceholder" text="Short description shown to members when choosing this product." />'></textarea>
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
                                <input name="minimumAmount" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="0.00" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.maximumAmount" text="Maximum Loan Amount" />
                                <input name="maximumAmount" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder='<spring:message code="admin.settings.loanProducts.maximumAmountPlaceholder" text="Leave blank for no product cap" />' />
                            </label>

                            <div class="savings-ratio-grid md:col-span-2" data-savings-ratio-group>
                                <input name="maxLoanSavingsPercent" type="hidden" value="300.00" data-savings-percent />
                                <div class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    <spring:message code="admin.settings.loanProducts.savingsMultiple" text="Loan Amount Limit By Savings" />
                                    <div class="savings-ratio-mode-toggle mt-2" data-savings-ratio-mode-toggle>
                                        <button type="button" class="is-active" data-savings-ratio-mode="multiple"><spring:message code="admin.settings.loanProducts.savingsMultipleMode" text="Multiple" /></button>
                                        <button type="button" data-savings-ratio-mode="fraction"><spring:message code="admin.settings.loanProducts.savingsFractionMode" text="Fraction" /></button>
                                    </div>
                                    <div class="savings-multiplier-control mt-1">
                                        <input type="number" min="0.01" step="0.01" class="rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="3" aria-label="Loan savings multiplier" data-savings-multiplier />
                                        <span class="savings-multiplier-label"><spring:message code="admin.settings.loanProducts.savingsMultiplierSuffix" text="x of savings" /></span>
                                    </div>
                                    <div class="savings-fraction-control mt-1 hidden" data-savings-fraction-control>
                                        <input type="number" min="1" max="9" step="1" maxlength="1" class="rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="3" aria-label="Savings fraction numerator" data-savings-fraction-numerator />
                                        <span class="savings-fraction-divider">/</span>
                                        <input type="number" min="1" max="9" step="1" maxlength="1" class="rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="1" aria-label="Savings fraction denominator" data-savings-fraction-denominator />
                                    </div>
                                </div>
                            </div>
                            <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                <input name="freshFinancialDataRequired" type="checkbox" value="true" data-workflow-loaded-financial />
                                <span><spring:message code="admin.settings.workflow.requireLoadedFinancialData" text="Require Loaded Financial Data At Submission" /></span>
                            </label>
                            <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                <input name="allowApplicationWithActiveLoan" type="checkbox" value="true" />
                                <span><spring:message code="admin.settings.workflow.allowApplicationWithActiveLoan" text="Allow Application With Active Loan" /></span>
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
                                <spring:message code="admin.settings.loanProducts.insurancePercent" text="Insurance %" />
                                <input name="insurancePercent" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="1.50" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.annualInterestPercent" text="Annual Interest %" />
                                <input name="annualInterestPercent" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="10.00" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.amountReturnMethod" text="Amount Return Method" />
                                <select name="interestMethod" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                    <option value="FLAT_RATE" selected><spring:message code="admin.settings.loanProducts.flatRate" text="Flat Rate" /></option>
                                    <option value="REDUCING_BALANCE"><spring:message code="admin.settings.loanProducts.reducingBalance" text="Reducing Balance" /></option>
                                </select>
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.minRepaymentMonths" text="Min Repayment Months" />
                                <input name="minRepaymentMonths" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="1" />
                            </label>

                            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.maxRepaymentMonths" text="Max Repayment Months" />
                                <input name="maxRepaymentMonths" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="12" />
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
                            <div class="workflow-readonly-note">
                                <spring:message code="admin.settings.workflow.readonlyNote" text="New applications follow this saved workflow configuration. Applications already on review keep the workflow that was active when they were submitted." />
                            </div>

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
                                                <option value="1" selected>1</option>
                                                <option value="2">2</option>
                                                <option value="3">3</option>
                                                <option value="4">4</option>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'><spring:message code="admin.settings.workflow.managerLoanOfficerPriorityNote" text="Choose any open priority from 1 to 4." /></div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.loanOfficer" text="Loan Officer" /></div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                            <label class="workflow-checkbox-lock"><input name="loanOfficerReviewRequired" type="checkbox" value="true" ${settings.loanOfficerReviewRequired ? 'checked' : ''} data-workflow-loan-officer aria-label="Loan Officer required" /></label>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                            <select name="loanOfficerPriority" class="workflow-priority-select" data-workflow-loan-officer-priority>
                                                <option value="1">1</option>
                                                <option value="2" selected>2</option>
                                                <option value="3">3</option>
                                                <option value="4">4</option>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                            <c:choose>
                                                <c:when test="${settings.loanOfficerReviewRequired}">
                                                    <spring:message code="admin.settings.workflow.loanOfficerPriorityNote" text="Choose any open priority from 1 to 4." />
                                                </c:when>
                                                <c:otherwise>
                                                    <spring:message code="admin.settings.workflow.enableLoanOfficerNote" text="Enable Loan Officer review in tenant approval flow settings to use this stage." />
                                                </c:otherwise>
                                            </c:choose>
                                        </div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.committee" text="Board Committee" /></div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                            <label class="workflow-checkbox-lock"><input name="committeeReviewRequired" type="checkbox" value="true" ${settings.boardReviewRequired ? 'checked' : ''} data-workflow-committee aria-label="Committee required" /></label>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                            <select name="committeePriority" class="workflow-priority-select" data-workflow-committee-priority>
                                                <option value="1">1</option>
                                                <option value="2">2</option>
                                                <option value="3" selected>3</option>
                                                <option value="4">4</option>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                            <spring:message code="admin.settings.workflow.openPriorityNote" text="Choose any open priority from 1 to 4." />
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
                                                <option value="1">1</option>
                                                <option value="2">2</option>
                                                <option value="3">3</option>
                                                <option value="4" selected>4</option>
                                            </select>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                            <spring:message code="admin.settings.workflow.openPriorityNote" text="Choose any open priority from 1 to 4." />
                                        </div>
                                    </div>
                                    <div class="workflow-table-row">
                                        <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.disbursementOfficer" text="Disbursement Officer" /></div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                            <label class="workflow-checkbox-lock"><input name="disbursementOfficerRequired" type="checkbox" value="true" checked data-workflow-disbursement-officer aria-label="Disbursement Officer required" /></label>
                                            <input type="hidden" name="disbursementOfficerRequired" value="false" />
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                            <select class="workflow-priority-select" disabled><option selected>5</option></select>
                                        </div>
                                        <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                            <spring:message code="admin.settings.workflow.disbursementOfficerNote" text="Role requirement for final manual release." />
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title"><spring:message code="admin.settings.workflow.committeeRules" text="Board Committee" /></p>
                                <div class="workflow-support-grid">
                                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                        <spring:message code="admin.settings.workflow.committeeReviewersAssigned" text="Committee Reviewers Assigned" />
                                        <input name="committeeMinimumVotes" type="number" min="1" max="15" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="2" data-number-range-max="15" data-workflow-committee-votes />
                                    </label>
                                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                        <spring:message code="admin.settings.workflow.committeeApprovalsNeeded" text="Minimum Committee Approvals" />
                                        <input name="committeeApprovalThreshold" type="number" min="1" max="15" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="2" data-number-range-max="15" data-workflow-committee-threshold />
                                    </label>
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title">Applicant</p>
                                <div class="workflow-support-grid">
                                    <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                        <input name="applicantAttachmentRequired" type="checkbox" value="true" />
                                        <span>Require applicant attachment while applying</span>
                                    </label>
                                    <input type="hidden" name="applicantAttachmentRequired" value="false" />
                                </div>
                            </div>

                            <div class="workflow-subsection">
                                <p class="workflow-subsection-title"><spring:message code="role.disbursementOfficer" text="Disbursement Officer" /></p>
                                <div class="workflow-support-grid">
                                    <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                        <input name="disbursementProofRequired" type="checkbox" value="true" checked />
                                        <span>Require proof attachment before disbursement</span>
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
                                        <input name="guarantorMinimumSavings" type="text" inputmode="decimal" data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="0.00" />
                                    </label>
                                    <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                        <input name="guarantorMinSavingsCheckRequired" type="checkbox" value="true" />
                                        <span>Check guarantor minimum savings before selection</span>
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
                        <button type="submit" class="app-btn btn-primary"><spring:message code="admin.settings.loanProducts.add" text="Add loan product" /></button>
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
                  data-active-board-members="${activeBoardMemberCount}"
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
                            <input name="displayOrder" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.resolvedDisplayOrder}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.name" text="Loan Product Name" />
                            <input name="productName" type="text" <c:if test="${product.loanType eq 'CUSTOMIZED_LOAN'}">required</c:if> maxlength="120" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${fn:escapeXml(product.displayName)}" data-product-name-field />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.status" text="Product Status" />
                            <select name="productStatus" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                <option value="DRAFT" ${product.status eq 'DRAFT' ? 'selected' : ''}><spring:message code="status.draft" text="Draft" /></option>
                                <option value="ACTIVE" ${product.status eq 'ACTIVE' ? 'selected' : ''}><spring:message code="status.active" text="Active" /></option>
                                <option value="SUSPENDED" ${product.status eq 'SUSPENDED' ? 'selected' : ''}><spring:message code="status.suspended" text="Suspended" /></option>
                                <option value="RETIRED" ${product.status eq 'RETIRED' ? 'selected' : ''}><spring:message code="status.retired" text="Retired" /></option>
                            </select>
                        </label>

                        <label class="product-identity-description block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="common.description" text="Description" />
                            <textarea name="productDescription" rows="3" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder='<spring:message code="admin.settings.loanProducts.descriptionPlaceholder" text="Short description shown to members when choosing this product." />'>${fn:escapeXml(product.productDescription)}</textarea>
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
                            <input name="minimumAmount" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.minimumAmount}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.maximumAmount" text="Maximum Loan Amount" />
                            <input name="maximumAmount" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maximumAmount}" />
                        </label>

                        <div class="savings-ratio-grid md:col-span-2" data-savings-ratio-group>
                            <input name="maxLoanSavingsPercent" type="hidden" value="${product.maxLoanSavingsRatio * 100}" data-savings-percent />
                            <div class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.savingsMultiple" text="Loan Amount Limit By Savings" />
                                <div class="savings-ratio-mode-toggle mt-2" data-savings-ratio-mode-toggle>
                                    <button type="button" class="is-active" data-savings-ratio-mode="multiple"><spring:message code="admin.settings.loanProducts.savingsMultipleMode" text="Multiple" /></button>
                                    <button type="button" data-savings-ratio-mode="fraction"><spring:message code="admin.settings.loanProducts.savingsFractionMode" text="Fraction" /></button>
                                </div>
                                <div class="savings-multiplier-control mt-1">
                                    <input type="number" min="0.01" step="0.01" class="rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" aria-label="Loan savings multiplier" data-savings-multiplier />
                                    <span class="savings-multiplier-label"><spring:message code="admin.settings.loanProducts.savingsMultiplierSuffix" text="x of savings" /></span>
                                </div>
                                <div class="savings-fraction-control mt-1 hidden" data-savings-fraction-control>
                                    <input type="number" min="1" max="9" step="1" maxlength="1" class="rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" aria-label="Savings fraction numerator" data-savings-fraction-numerator />
                                    <span class="savings-fraction-divider">/</span>
                                    <input type="number" min="1" max="9" step="1" maxlength="1" class="rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" aria-label="Savings fraction denominator" data-savings-fraction-denominator />
                                </div>
                            </div>
                        </div>
                        <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                            <input name="freshFinancialDataRequired" type="checkbox" value="true" ${product.freshFinancialDataRequired ? 'checked' : ''} data-workflow-loaded-financial />
                            <span><spring:message code="admin.settings.workflow.requireLoadedFinancialData" text="Require Loaded Financial Data At Submission" /></span>
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
                            <spring:message code="admin.settings.loanProducts.insurancePercent" text="Insurance %" />
                            <input name="insurancePercent" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.insuranceRate * 100}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.annualInterestPercent" text="Annual Interest %" />
                            <input name="annualInterestPercent" type="number" min="0" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.interestRate * 100}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.amountReturnMethod" text="Amount Return Method" />
                            <select name="interestMethod" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                <option value="FLAT_RATE" ${product.interestMethod eq 'FLAT_RATE' ? 'selected' : ''}><spring:message code="admin.settings.loanProducts.flatRate" text="Flat Rate" /></option>
                                <option value="REDUCING_BALANCE" ${product.interestMethod eq 'REDUCING_BALANCE' ? 'selected' : ''}><spring:message code="admin.settings.loanProducts.reducingBalance" text="Reducing Balance" /></option>
                            </select>
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.minRepaymentMonths" text="Min Repayment Months" />
                            <input name="minRepaymentMonths" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.minimumRepaymentMonths}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.maxRepaymentMonths" text="Max Repayment Months" />
                            <input name="maxRepaymentMonths" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maxRepaymentMonths}" />
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
                        <div class="workflow-readonly-note">
                            <spring:message code="admin.settings.workflow.readonlyNote" text="New applications follow this saved workflow configuration. Applications already on review keep the workflow that was active when they were submitted." />
                        </div>

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
                                            <option value="1" ${product.resolvedManagerPriority eq 1 ? 'selected' : ''}>1</option>
                                            <option value="2" ${product.resolvedManagerPriority eq 2 ? 'selected' : ''}>2</option>
                                            <option value="3" ${product.resolvedManagerPriority eq 3 ? 'selected' : ''}>3</option>
                                            <option value="4" ${product.resolvedManagerPriority eq 4 ? 'selected' : ''}>4</option>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'><spring:message code="admin.settings.workflow.managerLoanOfficerPriorityNote" text="Choose any open priority from 1 to 4." /></div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.loanOfficer" text="Loan Officer" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="loanOfficerReviewRequired" type="checkbox" value="true" ${(product.loanOfficerReviewRequired == true || (product.loanOfficerReviewRequired == null && settings.loanOfficerReviewRequired)) ? 'checked' : ''} data-workflow-loan-officer aria-label="Loan Officer required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="loanOfficerPriority" class="workflow-priority-select" data-workflow-loan-officer-priority>
                                            <option value="1" ${product.resolvedLoanOfficerPriority eq 1 ? 'selected' : ''}>1</option>
                                            <option value="2" ${product.resolvedLoanOfficerPriority eq 2 ? 'selected' : ''}>2</option>
                                            <option value="3" ${product.resolvedLoanOfficerPriority eq 3 ? 'selected' : ''}>3</option>
                                            <option value="4" ${product.resolvedLoanOfficerPriority eq 4 ? 'selected' : ''}>4</option>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <c:choose>
                                            <c:when test="${product.loanOfficerReviewRequired == true || (product.loanOfficerReviewRequired == null && settings.loanOfficerReviewRequired)}">
                                                <spring:message code="admin.settings.workflow.loanOfficerPriorityNote" text="Choose any open priority from 1 to 4." />
                                            </c:when>
                                            <c:otherwise>
                                                <spring:message code="admin.settings.workflow.enableLoanOfficerNote" text="Enable Loan Officer review in tenant approval flow settings to use this stage." />
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.committee" text="Board Committee" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="committeeReviewRequired" type="checkbox" value="true" ${product.committeeReviewRequired ? 'checked' : ''} data-workflow-committee aria-label="Committee required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="committeePriority" class="workflow-priority-select" data-workflow-committee-priority>
                                            <option value="1" ${product.resolvedCommitteePriority == 1 ? 'selected' : ''}>1</option>
                                            <option value="2" ${product.resolvedCommitteePriority == 2 ? 'selected' : ''}>2</option>
                                            <option value="3" ${product.resolvedCommitteePriority == 3 ? 'selected' : ''}>3</option>
                                            <option value="4" ${product.resolvedCommitteePriority == 4 ? 'selected' : ''}>4</option>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.openPriorityNote" text="Choose any open priority from 1 to 4." />
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
                                            <option value="1" ${product.resolvedAccountantPriority == 1 ? 'selected' : ''}>1</option>
                                            <option value="2" ${product.resolvedAccountantPriority == 2 ? 'selected' : ''}>2</option>
                                            <option value="3" ${product.resolvedAccountantPriority == 3 ? 'selected' : ''}>3</option>
                                            <option value="4" ${product.resolvedAccountantPriority == 4 ? 'selected' : ''}>4</option>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.openPriorityNote" text="Choose any open priority from 1 to 4." />
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.disbursementOfficer" text="Disbursement Officer" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="disbursementOfficerRequired" type="checkbox" value="true" ${product.disbursementOfficerRequired != false ? 'checked' : ''} data-workflow-disbursement-officer aria-label="Disbursement Officer required" /></label>
                                        <input type="hidden" name="disbursementOfficerRequired" value="false" />
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select class="workflow-priority-select" disabled><option selected>5</option></select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.disbursementOfficerNote" text="Role requirement for final manual release." />
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="admin.settings.workflow.committeeRules" text="Board Committee" /></p>
                            <div class="workflow-support-grid">
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    <spring:message code="admin.settings.workflow.committeeReviewersAssigned" text="Committee Reviewers Assigned" />
                                    <input name="committeeMinimumVotes" type="number" min="1" max="15" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.resolvedCommitteeMinimumVotes}" data-number-range-max="15" data-workflow-committee-votes />
                                </label>
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    <spring:message code="admin.settings.workflow.committeeApprovalsNeeded" text="Minimum Committee Approvals" />
                                    <input name="committeeApprovalThreshold" type="number" min="1" max="15" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.resolvedCommitteeApprovalThreshold}" data-number-range-max="15" data-workflow-committee-threshold />
                                </label>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title">Applicant</p>
                            <div class="workflow-support-grid">
                                <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    <input name="applicantAttachmentRequired" type="checkbox" value="true" ${product.applicantAttachmentRequired ? 'checked' : ''} />
                                    <span>Require applicant attachment while applying</span>
                                </label>
                                <input type="hidden" name="applicantAttachmentRequired" value="false" />
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="role.disbursementOfficer" text="Disbursement Officer" /></p>
                            <div class="workflow-support-grid">
                                <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    <input name="disbursementProofRequired" type="checkbox" value="true" ${product.disbursementProofRequired != false ? 'checked' : ''} />
                                    <span>Require proof attachment before disbursement</span>
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
                                    <span>Check guarantor minimum savings before selection</span>
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
<spring:message code="admin.settings.workflow.warning.duplicatePriorities" text="Each enabled review stage must use a different priority from 1 to 4." var="warningDuplicatePriorities" />
<spring:message code="admin.settings.workflow.warning.noCommitteeReviewers" text="Committee review is configured, but there are no active board members available for assignment." var="warningNoCommitteeReviewers" />
<spring:message code="admin.settings.workflow.warning.committeeVotesTooHigh" text="Committee minimum votes are higher than the active board member count." var="warningCommitteeVotesTooHigh" />
<spring:message code="admin.settings.workflow.warning.committeeThresholdTooHigh" text="Committee approval threshold cannot be greater than committee minimum votes." var="warningCommitteeThresholdTooHigh" />
<spring:message code="admin.settings.workflow.warning.committeeAccountantSamePriority" text="Committee and Accountant cannot share the same priority slot." var="warningCommitteeAccountantSamePriority" />
<spring:message code="admin.settings.workflow.warning.noAccountants" text="Accountant review is enabled for this product, but there are no active accountants available." var="warningNoAccountants" />
<spring:message code="admin.settings.workflow.warning.noDisbursementOfficers" text="Disbursement Officer is required, but there are no active Disbursement Officers assigned yet." var="warningNoDisbursementOfficers" />
<spring:message code="admin.settings.workflow.warning.noDisbursementClaimHolders" text="Disbursement Officer is optional, but no active staff user has both disbursement claims." var="warningNoDisbursementClaimHolders" />
<spring:message code="admin.settings.workflow.warning.loadedFinancialRequired" text="Members must load current financial data before this product can move into review." var="warningLoadedFinancialRequired" />
<spring:message code="admin.settings.workflow.warning.guarantorPrefix" text="This product waits for" var="warningGuarantorPrefix" />
<spring:message code="admin.settings.workflow.warning.guarantorApproval" text="guarantor approval" var="warningGuarantorApproval" />
<spring:message code="admin.settings.workflow.warning.guarantorApprovals" text="guarantor approvals" var="warningGuarantorApprovals" />
<spring:message code="admin.settings.workflow.warning.guarantorSuffix" text="before the review flow starts." var="warningGuarantorSuffix" />
<spring:message code="admin.settings.workflow.runtime.member" text="Member" var="runtimeMember" />
<spring:message code="admin.settings.workflow.runtime.loadedFinancialData" text="Loaded Financial Data" var="runtimeLoadedFinancialData" />
<spring:message code="admin.settings.workflow.runtime.guarantor" text="Guarantor" var="runtimeGuarantor" />
<spring:message code="admin.settings.workflow.runtime.guarantors" text="Guarantors" var="runtimeGuarantors" />
<spring:message code="admin.settings.workflow.runtime.fixManagerLoanOfficerPriority" text="Fix Priority Slots" var="runtimeFixManagerLoanOfficerPriority" />
<spring:message code="admin.settings.workflow.runtime.disbursementRelease" text="Disbursement Release" var="runtimeDisbursementRelease" />
<spring:message code="role.manager" text="Manager" var="runtimeManager" />
<spring:message code="role.loanOfficer" text="Loan Officer" var="runtimeLoanOfficer" />
<spring:message code="role.committee" text="Board Committee" var="runtimeCommittee" />
<spring:message code="role.accountant" text="Accountant" var="runtimeAccountant" />
<spring:message code="role.disbursementOfficer" text="Disbursement Officer" var="runtimeDisbursementOfficer" />
<spring:message code="admin.settings.validation.displayOrder" text="Display order must be 1 or higher." var="validationDisplayOrder" />
<spring:message code="admin.settings.validation.productNameRequired" text="Enter a loan product name." var="validationProductNameRequired" />
<spring:message code="admin.settings.validation.productNameLength" text="Loan product name must be 120 characters or fewer." var="validationProductNameLength" />
<spring:message code="admin.settings.validation.descriptionLength" text="Keep the description within 500 characters." var="validationDescriptionLength" />
<spring:message code="admin.settings.validation.minimumAmount" text="Minimum amount cannot be negative." var="validationMinimumAmount" />
<spring:message code="admin.settings.validation.maximumAmountPositive" text="Maximum amount must be greater than zero." var="validationMaximumAmountPositive" />
<spring:message code="admin.settings.validation.maximumBelowMinimum" text="Maximum amount cannot be lower than the minimum amount." var="validationMaximumBelowMinimum" />
<spring:message code="admin.settings.validation.savingsPercent" text="Loan savings multiple must be greater than zero." var="validationSavingsPercent" />
<spring:message code="admin.settings.validation.insurancePercent" text="Insurance percentage cannot be negative." var="validationInsurancePercent" />
<spring:message code="admin.settings.validation.annualInterestPercent" text="Annual interest percentage cannot be negative." var="validationAnnualInterestPercent" />
<spring:message code="admin.settings.validation.minRepaymentMonths" text="Minimum repayment period must be at least 1 month." var="validationMinRepaymentMonths" />
<spring:message code="admin.settings.validation.maxRepaymentMonths" text="Maximum repayment period must be at least 1 month." var="validationMaxRepaymentMonths" />
<spring:message code="admin.settings.validation.maxRepaymentBelowMin" text="Maximum repayment period cannot be lower than the minimum repayment period." var="validationMaxRepaymentBelowMin" />
<spring:message code="admin.settings.validation.guarantorsRequired" text="Guarantors required cannot be negative." var="validationGuarantorsRequired" />
<spring:message code="admin.settings.validation.countRange" text="Enter a number from 0 to 15." var="validationCountRange" />
<spring:message code="admin.settings.validation.committeeCountRange" text="Enter a number from 1 to 15." var="validationCommitteeCountRange" />
<spring:message code="admin.settings.validation.noReviewStage" text="Enable at least one review or approval stage before disbursement." var="validationNoReviewStage" />
<spring:message code="admin.settings.validation.managerPriority" text="Manager priority must be between 1 and 4." var="validationManagerPriority" />
<spring:message code="admin.settings.validation.loanOfficerPriority" text="Loan Officer priority must be between 1 and 4." var="validationLoanOfficerPriority" />
<spring:message code="admin.settings.validation.openPriority" text="Choose any open priority from 1 to 4." var="validationOpenPriority" />
<spring:message code="admin.settings.validation.managerLoanOfficerDifferentPriority" text="Choose different priorities for Manager and Loan Officer." var="validationManagerLoanOfficerDifferentPriority" />
<spring:message code="admin.settings.validation.assignLoanOfficer" text="Assign at least one active Loan Officer before using this stage." var="validationAssignLoanOfficer" />
<spring:message code="admin.settings.validation.assignCommitteeReviewer" text="Assign at least one active committee reviewer before using this stage." var="validationAssignCommitteeReviewer" />
<spring:message code="admin.settings.validation.committeeMinimumVotes" text="Committee reviewers assigned must be at least 1." var="validationCommitteeMinimumVotes" />
<spring:message code="admin.settings.validation.committeeApprovalThreshold" text="Committee approvals needed must be at least 1." var="validationCommitteeApprovalThreshold" />
<spring:message code="admin.settings.validation.committeeThresholdAboveVotes" text="Committee approvals needed cannot be greater than committee reviewers assigned." var="validationCommitteeThresholdAboveVotes" />
<spring:message code="admin.settings.validation.committeeVotesAboveActive" text="Committee reviewers assigned cannot exceed the active board member count." var="validationCommitteeVotesAboveActive" />
<spring:message code="admin.settings.validation.committeeThresholdAboveActive" text="Committee approvals needed cannot exceed the active board member count." var="validationCommitteeThresholdAboveActive" />
<spring:message code="admin.settings.validation.committeeAccountantDifferentPriority" text="Committee and Accountant cannot share the same priority slot." var="validationCommitteeAccountantDifferentPriority" />
<spring:message code="admin.settings.validation.assignAccountant" text="Assign at least one active Accountant before using this stage." var="validationAssignAccountant" />
<spring:message code="admin.settings.validation.assignDisbursementOfficer" text="Assign at least one active Disbursement Officer before requiring this role." var="validationAssignDisbursementOfficer" />
<spring:message code="admin.settings.validation.assignDisbursementClaims" text="Grant both disbursement claims to at least one active staff user before removing this role requirement." var="validationAssignDisbursementClaims" />
<spring:message code="admin.settings.validation.applicationFee" text="Application fee cannot be negative." var="validationApplicationFee" />
<script>
    (() => {
        const body = document.body;
        const productSettingsText = {
            warningNoReviewStage: "${warningNoReviewStage}",
            warningNoLoanOfficers: "${warningNoLoanOfficers}",
            warningManagerLoanOfficerSamePriority: "${warningManagerLoanOfficerSamePriority}",
            warningDuplicatePriorities: "${warningDuplicatePriorities}",
            warningNoCommitteeReviewers: "${warningNoCommitteeReviewers}",
            warningCommitteeVotesTooHigh: "${warningCommitteeVotesTooHigh}",
            warningCommitteeThresholdTooHigh: "${warningCommitteeThresholdTooHigh}",
            warningCommitteeAccountantSamePriority: "${warningCommitteeAccountantSamePriority}",
            warningNoAccountants: "${warningNoAccountants}",
            warningNoDisbursementOfficers: "${warningNoDisbursementOfficers}",
            warningNoDisbursementClaimHolders: "${warningNoDisbursementClaimHolders}",
            warningLoadedFinancialRequired: "${warningLoadedFinancialRequired}",
            warningGuarantorPrefix: "${warningGuarantorPrefix}",
            warningGuarantorApproval: "${warningGuarantorApproval}",
            warningGuarantorApprovals: "${warningGuarantorApprovals}",
            warningGuarantorSuffix: "${warningGuarantorSuffix}",
            runtimeMember: "${runtimeMember}",
            runtimeLoadedFinancialData: "${runtimeLoadedFinancialData}",
            runtimeGuarantor: "${runtimeGuarantor}",
            runtimeGuarantors: "${runtimeGuarantors}",
            runtimeFixManagerLoanOfficerPriority: "${runtimeFixManagerLoanOfficerPriority}",
            runtimeManager: "${runtimeManager}",
            runtimeLoanOfficer: "${runtimeLoanOfficer}",
            runtimeCommittee: "${runtimeCommittee}",
            runtimeAccountant: "${runtimeAccountant}",
            runtimeDisbursementOfficer: "${runtimeDisbursementOfficer}",
            runtimeDisbursementRelease: "${runtimeDisbursementRelease}",
            validationDisplayOrder: "${validationDisplayOrder}",
            validationProductNameRequired: "${validationProductNameRequired}",
            validationProductNameLength: "${validationProductNameLength}",
            validationDescriptionLength: "${validationDescriptionLength}",
            validationMinimumAmount: "${validationMinimumAmount}",
            validationMaximumAmountPositive: "${validationMaximumAmountPositive}",
            validationMaximumBelowMinimum: "${validationMaximumBelowMinimum}",
            validationSavingsPercent: "${validationSavingsPercent}",
            validationInsurancePercent: "${validationInsurancePercent}",
            validationAnnualInterestPercent: "${validationAnnualInterestPercent}",
            validationMinRepaymentMonths: "${validationMinRepaymentMonths}",
            validationMaxRepaymentMonths: "${validationMaxRepaymentMonths}",
            validationMaxRepaymentBelowMin: "${validationMaxRepaymentBelowMin}",
            validationGuarantorsRequired: "${validationGuarantorsRequired}",
            validationCountRange: "${validationCountRange}",
            validationCommitteeCountRange: "${validationCommitteeCountRange}",
            validationNoReviewStage: "${validationNoReviewStage}",
            validationManagerPriority: "${validationManagerPriority}",
            validationLoanOfficerPriority: "${validationLoanOfficerPriority}",
            validationOpenPriority: "${validationOpenPriority}",
            validationManagerLoanOfficerDifferentPriority: "${validationManagerLoanOfficerDifferentPriority}",
            validationAssignLoanOfficer: "${validationAssignLoanOfficer}",
            validationAssignCommitteeReviewer: "${validationAssignCommitteeReviewer}",
            validationCommitteeMinimumVotes: "${validationCommitteeMinimumVotes}",
            validationCommitteeApprovalThreshold: "${validationCommitteeApprovalThreshold}",
            validationCommitteeThresholdAboveVotes: "${validationCommitteeThresholdAboveVotes}",
            validationCommitteeVotesAboveActive: "${validationCommitteeVotesAboveActive}",
            validationCommitteeThresholdAboveActive: "${validationCommitteeThresholdAboveActive}",
            validationCommitteeAccountantDifferentPriority: "${validationCommitteeAccountantDifferentPriority}",
            validationAssignAccountant: "${validationAssignAccountant}",
            validationAssignDisbursementOfficer: "${validationAssignDisbursementOfficer}",
            validationAssignDisbursementClaims: "${validationAssignDisbursementClaims}",
            validationApplicationFee: "${validationApplicationFee}"
        };

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
            const raw = typeof input.value === 'string' ? input.value.replace(/,/g, '').trim() : '';
            if (raw === '') {
                return null;
            }
            const value = Number(raw);
            return Number.isFinite(value) ? value : Number.NaN;
        }

        function normalizeMoneyValue(value) {
            return String(value || '')
                .replace(/,/g, '')
                .replace(/[^\d.]/g, '')
                .replace(/(\..*)\./g, '$1');
        }

        function formatMoneyValue(value) {
            const raw = normalizeMoneyValue(value);
            if (raw === '') {
                return '';
            }
            const parts = raw.split('.');
            const grouped = parts[0].replace(/^0+(?=\d)/, '').replace(/\B(?=(\d{3})+(?!\d))/g, ',');
            return parts.length > 1 ? grouped + '.' + parts.slice(1).join('') : grouped;
        }

        function syncMoneyInput(input) {
            input.value = formatMoneyValue(input.value);
        }

        document.querySelectorAll('[data-money-input="true"]').forEach((input) => {
            syncMoneyInput(input);
            input.addEventListener('input', () => syncMoneyInput(input));
            input.addEventListener('blur', () => syncMoneyInput(input));
        });

        document.querySelectorAll('form').forEach((form) => {
            if (!form.querySelector('[data-money-input="true"]')) {
                return;
            }
            form.addEventListener('submit', () => {
                form.querySelectorAll('[data-money-input="true"]').forEach((input) => {
                    input.value = normalizeMoneyValue(input.value);
                });
            });
        });

        function enforceNumericTextLength(input) {
            if (!input || typeof input.value !== 'string') {
                return;
            }
            const maxLength = Number(input.getAttribute('data-numeric-maxlength') || 15);
            if (!Number.isFinite(maxLength) || maxLength <= 0 || input.value.length <= maxLength) {
                return;
            }
            input.value = input.value.slice(0, maxLength);
        }

        function enforceNumberRange(input) {
            if (!input || input.value === '') {
                return;
            }
            const value = Number(input.value);
            if (!Number.isFinite(value)) {
                return;
            }
            const minValue = input.hasAttribute('min') ? Number(input.getAttribute('min')) : Number.NaN;
            const maxValue = Number(input.getAttribute('data-number-range-max') || input.getAttribute('max'));
            if (Number.isFinite(minValue) && value < minValue) {
                input.value = String(minValue);
            } else if (Number.isFinite(maxValue) && value > maxValue) {
                input.value = String(maxValue);
            }
        }

        document.querySelectorAll('input[type="number"], input[inputmode="decimal"], input[data-money-input="true"]').forEach((input) => {
            input.setAttribute('maxlength', input.getAttribute('maxlength') || '15');
            input.setAttribute('data-numeric-maxlength', input.getAttribute('data-numeric-maxlength') || '15');
            enforceNumericTextLength(input);
            input.addEventListener('input', () => enforceNumericTextLength(input));
            input.addEventListener('change', () => enforceNumericTextLength(input));
        });

        document.querySelectorAll('input[data-number-range-max]').forEach((input) => {
            enforceNumberRange(input);
            input.addEventListener('input', () => enforceNumberRange(input));
            input.addEventListener('change', () => enforceNumberRange(input));
        });

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
                case 'managerReviewRequired': {
                    const field = form.querySelector('[data-workflow-manager]');
                    return field ? [field] : [];
                }
                case 'disbursementOfficerRequired': {
                    const field = form.querySelector('[data-workflow-disbursement-officer]');
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

        function revealFieldErrorTarget(target) {
            if (!target) {
                return;
            }
            const container = fieldErrorContainer(target) || target;
            const scrollBox = target.closest('.app-modal-scroll');
            if (scrollBox) {
                const targetRect = container.getBoundingClientRect();
                const scrollRect = scrollBox.getBoundingClientRect();
                const desiredTop = scrollBox.scrollTop + targetRect.top - scrollRect.top - Math.max(24, scrollBox.clientHeight * 0.25);
                scrollBox.scrollTo({
                    top: Math.max(0, desiredTop),
                    behavior: 'smooth'
                });
            } else if (typeof container.scrollIntoView === 'function') {
                container.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
            if (target.matches && target.matches('input, select, textarea')) {
                window.setTimeout(() => target.focus({ preventScroll: true }), 180);
            }
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
            const managerToggle = form.querySelector('[data-workflow-manager]');
            const accountantToggle = form.querySelector('[data-workflow-accountant]');
            const disbursementOfficerToggle = form.querySelector('[data-workflow-disbursement-officer]');
            const accountantPriority = form.querySelector('[data-workflow-accountant-priority]');
            const managerPriority = form.querySelector('[data-workflow-manager-priority]');
            const loanOfficerPriority = form.querySelector('[data-workflow-loan-officer-priority]');
            const managerStartRadio = form.querySelector('[data-workflow-start-radio="manager"]');
            const loanOfficerStartRadio = form.querySelector('[data-workflow-start-radio="loanOfficer"]');
            const workflowStartStageField = form.querySelector('[data-workflow-start-stage-field]');
            const productNameField = form.querySelector('[data-product-name-field]');
            const productCodeField = form.querySelector('[data-product-code-field][data-product-code-generated="true"]');
            const savingsPercentInput = form.querySelector('[data-savings-percent]');
            const savingsMultiplierInput = form.querySelector('[data-savings-multiplier]');
            const savingsFractionNumerator = form.querySelector('[data-savings-fraction-numerator]');
            const savingsFractionDenominator = form.querySelector('[data-savings-fraction-denominator]');
            const savingsMultiplierControl = form.querySelector('.savings-multiplier-control');
            const savingsFractionControl = form.querySelector('[data-savings-fraction-control]');
            const savingsModeToggle = form.querySelector('[data-savings-ratio-mode-toggle]');
            let savingsRatioMode = 'multiple';
            let syncingSavingsRatio = false;

            if (!committeeToggle || !warningsRoot || !runtimeRoot) {
                return;
            }

            const tenantLoanOfficerEnabled = form.dataset.tenantLoanOfficerEnabled === 'true';
            const tenantBoardEnabled = form.dataset.tenantBoardEnabled === 'true';
            const activeLoanOfficers = Number(form.dataset.activeLoanOfficers || '0');
            const activeBoardMembers = Number(form.dataset.activeBoardMembers || '0');
            const activeAccountants = Number(form.dataset.activeAccountants || '0');
            const activeDisbursementOfficers = Number(form.dataset.activeDisbursementOfficers || '0');
            const activeDisbursementClaimHolders = Number(form.dataset.activeDisbursementClaimHolders || '0');

            function priorityEntries(managerEnabled, loanOfficerEnabled, committeeEnabled, accountantEnabled, managerValue, loanOfficerValue, committeeValue, accountantValue) {
                return [
                    { key: 'managerPriority', label: productSettingsText.runtimeManager, enabled: managerEnabled, field: managerPriority, value: managerValue },
                    { key: 'loanOfficerPriority', label: productSettingsText.runtimeLoanOfficer, enabled: loanOfficerEnabled, field: loanOfficerPriority, value: loanOfficerValue },
                    { key: 'committeePriority', label: productSettingsText.runtimeCommittee, enabled: committeeEnabled, field: committeePriority, value: committeeValue },
                    { key: 'accountantPriority', label: productSettingsText.runtimeAccountant, enabled: accountantEnabled, field: accountantPriority, value: accountantValue }
                ].filter((entry) => entry.enabled && entry.field && Number.isFinite(entry.value));
            }

            function duplicatePriorityEntries(entries) {
                const byPriority = new Map();
                entries.forEach((entry) => {
                    const existing = byPriority.get(entry.value) || [];
                    existing.push(entry);
                    byPriority.set(entry.value, existing);
                });
                return Array.from(byPriority.values()).filter((group) => group.length > 1).flat();
            }

            function clearLivePriorityErrors() {
                form.querySelectorAll('.field-error-text[data-live-priority-error]').forEach((node) => node.remove());
                [managerPriority, loanOfficerPriority, committeePriority, accountantPriority].filter(Boolean).forEach((field) => {
                    if (field.dataset.livePriorityField === 'true') {
                        field.classList.remove('field-error-input');
                        field.removeAttribute('aria-invalid');
                        delete field.dataset.livePriorityField;
                    }
                    const container = fieldErrorContainer(field);
                    if (container && !container.querySelector('.field-error-text')) {
                        container.classList.remove('field-error-container');
                    }
                });
            }

            function markLivePriorityError(field, message) {
                const container = fieldErrorContainer(field);
                if (!field || !container) {
                    return;
                }
                field.classList.add('field-error-input');
                field.setAttribute('aria-invalid', 'true');
                field.dataset.livePriorityField = 'true';
                container.classList.add('field-error-container');
                if (container.querySelector('[data-live-priority-error]')) {
                    return;
                }
                const note = document.createElement('p');
                note.className = 'field-error-text';
                note.setAttribute('data-live-priority-error', 'true');
                note.setAttribute('data-field-error-for', field.name);
                note.textContent = message;
                container.appendChild(note);
            }

            function syncProductCode() {
                if (!productCodeField || !productNameField) {
                    return;
                }
                productCodeField.value = generateProductCode(productNameField.value);
            }

            function approximateFraction(ratio) {
                if (!Number.isFinite(ratio) || ratio <= 0) {
                    return { numerator: 1, denominator: 1 };
                }
                let bestNumerator = 1;
                let bestDenominator = 1;
                let bestError = Math.abs(ratio - 1);
                for (let denominator = 1; denominator <= 9; denominator += 1) {
                    const numerator = Math.max(1, Math.min(9, Math.round(ratio * denominator)));
                    const error = Math.abs(ratio - numerator / denominator);
                    if (error < bestError) {
                        bestNumerator = numerator;
                        bestDenominator = denominator;
                        bestError = error;
                    }
                }
                return { numerator: bestNumerator, denominator: bestDenominator };
            }

            function renderSavingsRatioMode() {
                if (savingsMultiplierControl) {
                    savingsMultiplierControl.classList.toggle('hidden', savingsRatioMode !== 'multiple');
                }
                if (savingsFractionControl) {
                    savingsFractionControl.classList.toggle('hidden', savingsRatioMode !== 'fraction');
                }
                savingsModeToggle?.querySelectorAll('[data-savings-ratio-mode]').forEach((button) => {
                    button.classList.toggle('is-active', button.dataset.savingsRatioMode === savingsRatioMode);
                });
            }

            function syncSavingsMultiplierFromPercent() {
                if (!savingsPercentInput || !savingsMultiplierInput || syncingSavingsRatio) {
                    return;
                }
                const percent = Number(savingsPercentInput.value);
                if (!Number.isFinite(percent) || percent <= 0) {
                    return;
                }
                syncingSavingsRatio = true;
                savingsMultiplierInput.value = (percent / 100).toFixed(2);
                if (savingsFractionNumerator && savingsFractionDenominator) {
                    const fraction = approximateFraction(percent / 100);
                    savingsFractionNumerator.value = String(fraction.numerator);
                    savingsFractionDenominator.value = String(fraction.denominator);
                }
                syncingSavingsRatio = false;
            }

            function syncSavingsPercentFromMultiplier() {
                if (!savingsPercentInput || !savingsMultiplierInput || syncingSavingsRatio) {
                    return;
                }
                const multiplier = Number(savingsMultiplierInput.value);
                if (!Number.isFinite(multiplier) || multiplier <= 0) {
                    return;
                }
                syncingSavingsRatio = true;
                savingsPercentInput.value = (multiplier * 100).toFixed(2);
                if (savingsFractionNumerator && savingsFractionDenominator) {
                    const fraction = approximateFraction(multiplier);
                    savingsFractionNumerator.value = String(fraction.numerator);
                    savingsFractionDenominator.value = String(fraction.denominator);
                }
                syncingSavingsRatio = false;
            }

            function clampSavingsFractionField(field) {
                if (!field) {
                    return null;
                }
                const rawDigits = String(field.value || '').replace(/\D/g, '');
                const digit = rawDigits ? Number(rawDigits.charAt(0)) : null;
                const clamped = digit == null || !Number.isFinite(digit)
                    ? null
                    : Math.max(1, Math.min(9, digit));
                if (clamped == null) {
                    field.value = '';
                    return null;
                }
                field.value = String(clamped);
                return clamped;
            }

            function syncSavingsPercentFromFraction() {
                if (!savingsPercentInput || !savingsFractionNumerator || !savingsFractionDenominator || syncingSavingsRatio) {
                    return;
                }
                const numerator = clampSavingsFractionField(savingsFractionNumerator);
                const denominator = clampSavingsFractionField(savingsFractionDenominator);
                if (!Number.isFinite(numerator) || !Number.isFinite(denominator) || numerator <= 0 || denominator <= 0) {
                    return;
                }
                const ratio = numerator / denominator;
                syncingSavingsRatio = true;
                savingsPercentInput.value = (ratio * 100).toFixed(2);
                if (savingsMultiplierInput) {
                    savingsMultiplierInput.value = ratio.toFixed(2);
                }
                syncingSavingsRatio = false;
            }

            function syncReviewPriorities(source) {
                if (!managerPriority || !loanOfficerPriority || !loanOfficerToggle) {
                    return;
                }
                const managerEnabled = !managerToggle || managerToggle.checked;
                const loanOfficerEnabled = loanOfficerToggle.checked;
                managerPriority.disabled = !managerEnabled;
                if (!loanOfficerEnabled) {
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

                if (!managerEnabled) {
                    loanOfficerPriority.disabled = false;
                    if (managerStartRadio) {
                        managerStartRadio.checked = false;
                        managerStartRadio.disabled = true;
                    }
                    if (loanOfficerStartRadio) {
                        loanOfficerStartRadio.checked = true;
                        loanOfficerStartRadio.disabled = false;
                    }
                    if (workflowStartStageField) {
                        workflowStartStageField.value = 'LOAN_OFFICER';
                    }
                    return;
                }

                loanOfficerPriority.disabled = false;
                if (managerStartRadio) {
                    managerStartRadio.disabled = false;
                }
                if (loanOfficerStartRadio) {
                    loanOfficerStartRadio.disabled = false;
                }

                if (source === 'manager-radio' && managerStartRadio && managerStartRadio.checked) {
                    if (Number(managerPriority.value) >= Number(loanOfficerPriority.value)) {
                        managerPriority.value = '1';
                    }
                } else if (source === 'loan-officer-radio' && loanOfficerStartRadio && loanOfficerStartRadio.checked) {
                    if (Number(loanOfficerPriority.value) >= Number(managerPriority.value)) {
                        loanOfficerPriority.value = '1';
                    }
                }

                const prioritiesAreDistinct = managerPriority.value !== loanOfficerPriority.value;
                if (prioritiesAreDistinct && managerStartRadio && loanOfficerStartRadio) {
                    managerStartRadio.checked = Number(managerPriority.value) < Number(loanOfficerPriority.value);
                    loanOfficerStartRadio.checked = Number(loanOfficerPriority.value) < Number(managerPriority.value);
                }
                if (workflowStartStageField) {
                    if (prioritiesAreDistinct) {
                        workflowStartStageField.value = Number(loanOfficerPriority.value) < Number(managerPriority.value) ? 'LOAN_OFFICER' : 'MANAGER';
                    } else {
                        workflowStartStageField.value = loanOfficerStartRadio && loanOfficerStartRadio.checked ? 'LOAN_OFFICER' : 'MANAGER';
                    }
                }
            }

            function syncSavedWorkflowStartStage() {
                if (!workflowStartStageField || !managerPriority || !loanOfficerPriority || !loanOfficerToggle || !loanOfficerToggle.checked) {
                    return;
                }
            }

            function applyRules() {
                const managerEnabled = !managerToggle || managerToggle.checked;
                const loanOfficerEnabled = loanOfficerToggle && loanOfficerToggle.checked;
                const committeeEnabled = committeeToggle.checked;
                const accountantEnabled = accountantToggle && accountantToggle.checked;
                const disbursementOfficerRequired = !disbursementOfficerToggle || disbursementOfficerToggle.checked;
                const guarantors = Math.max(0, numericValue(guarantorsInput, 0));
                const minimumVotes = Math.max(1, numericValue(committeeVotes, 1));
                const approvalThreshold = Math.max(1, numericValue(committeeThreshold, 1));
                const loadedFinancialRequired = loadedFinancialInput && loadedFinancialInput.checked;

                syncProductCode();
                syncReviewPriorities();

                const managerPriorityValue = Math.max(1, Math.min(4, numericValue(managerPriority, 1)));
                const loanOfficerPriorityValue = Math.max(1, Math.min(4, numericValue(loanOfficerPriority, 2)));
                const committeePriorityValue = Math.max(1, Math.min(4, numericValue(committeePriority, 3)));
                const accountantPriorityValue = Math.max(1, Math.min(4, numericValue(accountantPriority, 4)));
                if (committeePriority) {
                    committeePriority.value = String(committeePriorityValue);
                    committeePriority.disabled = !committeeEnabled;
                }
                if (accountantPriority) {
                    accountantPriority.value = String(accountantPriorityValue);
                    accountantPriority.disabled = !accountantEnabled;
                }
                const activePriorityEntries = priorityEntries(
                    managerEnabled,
                    loanOfficerEnabled,
                    committeeEnabled,
                    accountantEnabled,
                    managerPriorityValue,
                    loanOfficerPriorityValue,
                    committeePriorityValue,
                    accountantPriorityValue
                );
                const duplicatePriorities = duplicatePriorityEntries(activePriorityEntries);
                clearLivePriorityErrors();
                duplicatePriorities.forEach((entry) => markLivePriorityError(entry.field, productSettingsText.validationOpenPriority));

                const warnings = [];
                if (!managerEnabled && !loanOfficerEnabled && !committeeEnabled && !accountantEnabled) {
                    warnings.push(workflowWarning('danger', productSettingsText.warningNoReviewStage));
                }
                if (loanOfficerEnabled && activeLoanOfficers <= 0) {
                    warnings.push(workflowWarning('danger', productSettingsText.warningNoLoanOfficers));
                }
                if (duplicatePriorities.length > 0) {
                    warnings.push(workflowWarning('danger', productSettingsText.warningDuplicatePriorities));
                }

                if (committeeEnabled && activeBoardMembers <= 0) {
                    warnings.push(workflowWarning('danger', productSettingsText.warningNoCommitteeReviewers));
                }
                if (committeeEnabled && minimumVotes > activeBoardMembers && activeBoardMembers > 0) {
                    warnings.push(workflowWarning('danger', productSettingsText.warningCommitteeVotesTooHigh));
                }
                if (committeeEnabled && approvalThreshold > minimumVotes) {
                    warnings.push(workflowWarning('danger', productSettingsText.warningCommitteeThresholdTooHigh));
                }
                if (accountantEnabled && activeAccountants <= 0) {
                    warnings.push(workflowWarning('danger', productSettingsText.warningNoAccountants));
                }
                if (disbursementOfficerRequired && activeDisbursementOfficers <= 0) {
                    warnings.push(workflowWarning('danger', productSettingsText.warningNoDisbursementOfficers));
                }
                if (!disbursementOfficerRequired && activeDisbursementClaimHolders <= 0) {
                    warnings.push(workflowWarning('danger', productSettingsText.warningNoDisbursementClaimHolders));
                }
                if (loadedFinancialRequired) {
                    warnings.push(workflowWarning('info', productSettingsText.warningLoadedFinancialRequired));
                }
                if (guarantors > 0) {
                    warnings.push(workflowWarning('info', productSettingsText.warningGuarantorPrefix + ' ' + guarantors + ' ' + (guarantors === 1 ? productSettingsText.warningGuarantorApproval : productSettingsText.warningGuarantorApprovals) + ' ' + productSettingsText.warningGuarantorSuffix));
                }

                warningsRoot.innerHTML = '';
                warnings.forEach((warning) => warningsRoot.appendChild(warning));

                const runtimeLabels = [productSettingsText.runtimeMember];
                if (loadedFinancialRequired) {
                    runtimeLabels.push(productSettingsText.runtimeLoadedFinancialData);
                }
                if (guarantors > 0) {
                    runtimeLabels.push(guarantors + ' ' + (guarantors === 1 ? productSettingsText.runtimeGuarantor : productSettingsText.runtimeGuarantors));
                }
                if (duplicatePriorities.length > 0) {
                    runtimeLabels.push(productSettingsText.runtimeFixManagerLoanOfficerPriority);
                } else {
                    activePriorityEntries
                        .slice()
                        .sort((first, second) => first.value - second.value)
                        .forEach((entry) => runtimeLabels.push(entry.label));
                }
                runtimeLabels.push(disbursementOfficerRequired ? productSettingsText.runtimeDisbursementOfficer : productSettingsText.runtimeDisbursementRelease);
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
                const managerEnabled = !managerToggle || managerToggle.checked;
                const loanOfficerEnabled = loanOfficerToggle && loanOfficerToggle.checked;
                const committeeEnabled = committeeToggle && committeeToggle.checked;
                const accountantEnabled = accountantToggle && accountantToggle.checked;
                const disbursementOfficerRequired = !disbursementOfficerToggle || disbursementOfficerToggle.checked;

                if (displayOrder === null || Number.isNaN(displayOrder) || displayOrder < 1) {
                    addValidationError(errors, 'displayOrder', productSettingsText.validationDisplayOrder);
                }
                if (productName && !productName.value.trim()) {
                    addValidationError(errors, 'productName', productSettingsText.validationProductNameRequired);
                } else if (productName && productName.value.trim().length > 120) {
                    addValidationError(errors, 'productName', productSettingsText.validationProductNameLength);
                }
                if (productDescription && productDescription.value.length > 500) {
                    addValidationError(errors, 'productDescription', productSettingsText.validationDescriptionLength);
                }
                if (minimumAmount === null || Number.isNaN(minimumAmount) || minimumAmount < 0) {
                    addValidationError(errors, 'minimumAmount', productSettingsText.validationMinimumAmount);
                }
                if (maximumAmount !== null) {
                    if (Number.isNaN(maximumAmount) || maximumAmount <= 0) {
                        addValidationError(errors, 'maximumAmount', productSettingsText.validationMaximumAmountPositive);
                    } else if (minimumAmount !== null && !Number.isNaN(minimumAmount) && maximumAmount < minimumAmount) {
                        addValidationError(errors, 'maximumAmount', productSettingsText.validationMaximumBelowMinimum);
                    }
                }
                if (savingsPercent === null || Number.isNaN(savingsPercent) || savingsPercent <= 0) {
                    addValidationError(errors, 'maxLoanSavingsPercent', productSettingsText.validationSavingsPercent);
                }
                if (insurancePercent === null || Number.isNaN(insurancePercent) || insurancePercent < 0) {
                    addValidationError(errors, 'insurancePercent', productSettingsText.validationInsurancePercent);
                }
                if (annualInterestPercent === null || Number.isNaN(annualInterestPercent) || annualInterestPercent < 0) {
                    addValidationError(errors, 'annualInterestPercent', productSettingsText.validationAnnualInterestPercent);
                }
                if (minRepaymentMonths === null || Number.isNaN(minRepaymentMonths) || minRepaymentMonths < 1) {
                    addValidationError(errors, 'minRepaymentMonths', productSettingsText.validationMinRepaymentMonths);
                }
                if (maxRepaymentMonths === null || Number.isNaN(maxRepaymentMonths) || maxRepaymentMonths < 1) {
                    addValidationError(errors, 'maxRepaymentMonths', productSettingsText.validationMaxRepaymentMonths);
                } else if (minRepaymentMonths !== null && !Number.isNaN(minRepaymentMonths) && maxRepaymentMonths < minRepaymentMonths) {
                    addValidationError(errors, 'maxRepaymentMonths', productSettingsText.validationMaxRepaymentBelowMin);
                }
                if (guarantorsRequired === null || Number.isNaN(guarantorsRequired) || guarantorsRequired < 0 || guarantorsRequired > 15) {
                    addValidationError(errors, 'guarantorsRequired', productSettingsText.validationCountRange);
                }

                if (!managerEnabled && !loanOfficerEnabled && !committeeEnabled && !accountantEnabled) {
                    addValidationError(errors, 'managerReviewRequired', productSettingsText.validationNoReviewStage);
                }

                priorityEntries(
                    managerEnabled,
                    loanOfficerEnabled,
                    committeeEnabled,
                    accountantEnabled,
                    managerPriorityValue,
                    loanOfficerPriorityValue,
                    committeePriorityValue,
                    accountantPriorityValue
                ).forEach((entry) => {
                    if (entry.value < 1 || entry.value > 4) {
                        addValidationError(errors, entry.key, productSettingsText.validationOpenPriority);
                    }
                });
                duplicatePriorityEntries(priorityEntries(
                    managerEnabled,
                    loanOfficerEnabled,
                    committeeEnabled,
                    accountantEnabled,
                    managerPriorityValue,
                    loanOfficerPriorityValue,
                    committeePriorityValue,
                    accountantPriorityValue
                )).forEach((entry) => {
                    addValidationError(errors, entry.key, productSettingsText.validationOpenPriority);
                });

                if (loanOfficerEnabled) {
                    if (managerPriorityValue === null || Number.isNaN(managerPriorityValue) || managerPriorityValue < 1 || managerPriorityValue > 4) {
                        addValidationError(errors, 'managerPriority', productSettingsText.validationManagerPriority);
                    }
                    if (loanOfficerPriorityValue === null || Number.isNaN(loanOfficerPriorityValue) || loanOfficerPriorityValue < 1 || loanOfficerPriorityValue > 4) {
                        addValidationError(errors, 'loanOfficerPriority', productSettingsText.validationLoanOfficerPriority);
                    }
                    if (activeLoanOfficers <= 0) {
                        addValidationError(errors, 'loanOfficerReviewRequired', productSettingsText.validationAssignLoanOfficer);
                    }
                }

                if (committeeEnabled) {
                    if (activeBoardMembers <= 0) {
                        addValidationError(errors, 'committeeReviewRequired', productSettingsText.validationAssignCommitteeReviewer);
                    }
                    if (committeeVotesValue === null || Number.isNaN(committeeVotesValue) || committeeVotesValue < 1 || committeeVotesValue > 15) {
                        addValidationError(errors, 'committeeMinimumVotes', productSettingsText.validationCommitteeCountRange);
                    }
                    if (committeeThresholdValue === null || Number.isNaN(committeeThresholdValue) || committeeThresholdValue < 1 || committeeThresholdValue > 15) {
                        addValidationError(errors, 'committeeApprovalThreshold', productSettingsText.validationCommitteeCountRange);
                    }
                    if (!Number.isNaN(committeeVotesValue) && !Number.isNaN(committeeThresholdValue) && committeeThresholdValue > committeeVotesValue) {
                        addValidationError(errors, 'committeeMinimumVotes', productSettingsText.validationCommitteeThresholdAboveVotes);
                        addValidationError(errors, 'committeeApprovalThreshold', productSettingsText.validationCommitteeThresholdAboveVotes);
                    }
                    if (activeBoardMembers > 0 && !Number.isNaN(committeeVotesValue) && committeeVotesValue > activeBoardMembers) {
                        addValidationError(errors, 'committeeMinimumVotes', productSettingsText.validationCommitteeVotesAboveActive);
                    }
                    if (activeBoardMembers > 0 && !Number.isNaN(committeeThresholdValue) && committeeThresholdValue > activeBoardMembers) {
                        addValidationError(errors, 'committeeApprovalThreshold', productSettingsText.validationCommitteeThresholdAboveActive);
                    }
                }

                if (accountantEnabled && activeAccountants <= 0) {
                    addValidationError(errors, 'accountantReviewRequired', productSettingsText.validationAssignAccountant);
                }

                if (disbursementOfficerRequired && activeDisbursementOfficers <= 0) {
                    addValidationError(errors, 'disbursementOfficerRequired', productSettingsText.validationAssignDisbursementOfficer);
                }
                if (!disbursementOfficerRequired && activeDisbursementClaimHolders <= 0) {
                    addValidationError(errors, 'disbursementOfficerRequired', productSettingsText.validationAssignDisbursementClaims);
                }

                return errors;
            }

            [managerToggle, loanOfficerToggle, committeeToggle, committeeVotes, committeeThreshold, committeePriority, accountantToggle, accountantPriority, disbursementOfficerToggle, guarantorsInput, loadedFinancialInput, savingsPercentInput]
                .filter(Boolean)
                .forEach((field) => {
                    field.addEventListener('change', applyRules);
                    field.addEventListener('input', applyRules);
                });
            [savingsMultiplierInput]
                .filter(Boolean)
                .forEach((field) => {
                    field.addEventListener('change', () => {
                        syncSavingsPercentFromMultiplier();
                        applyRules();
                    });
                    field.addEventListener('input', () => {
                        syncSavingsPercentFromMultiplier();
                        applyRules();
                    });
                });
            [savingsFractionNumerator, savingsFractionDenominator]
                .filter(Boolean)
                .forEach((field) => {
                    field.addEventListener('change', () => {
                        syncSavingsPercentFromFraction();
                        applyRules();
                    });
                    field.addEventListener('input', () => {
                        syncSavingsPercentFromFraction();
                        applyRules();
                    });
                });
            savingsModeToggle?.querySelectorAll('[data-savings-ratio-mode]').forEach((button) => {
                button.addEventListener('click', () => {
                    const nextMode = button.dataset.savingsRatioMode === 'fraction' ? 'fraction' : 'multiple';
                    if (nextMode === savingsRatioMode) {
                        return;
                    }
                    if (savingsRatioMode === 'fraction') {
                        syncSavingsPercentFromFraction();
                    } else {
                        syncSavingsPercentFromMultiplier();
                    }
                    savingsRatioMode = nextMode;
                    syncSavingsMultiplierFromPercent();
                    renderSavingsRatioMode();
                    applyRules();
                });
            });
            if (savingsPercentInput) {
                savingsPercentInput.addEventListener('change', syncSavingsMultiplierFromPercent);
                savingsPercentInput.addEventListener('input', syncSavingsMultiplierFromPercent);
                syncSavingsMultiplierFromPercent();
            }
            renderSavingsRatioMode();

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
                revealFieldErrorTarget(firstTarget);
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
                    addValidationError(errors, 'applicationFee', productSettingsText.validationApplicationFee);
                }
                if (!errors.length) {
                    return;
                }
                event.preventDefault();
                errors.forEach((entry) => applyFieldError(form, entry.key, entry.message));
                const firstTarget = resolveFieldTargets(form, errors[0].key)[0];
                revealFieldErrorTarget(firstTarget);
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
                const firstServerError = modal.querySelector('.field-error-input');
                if (firstServerError) {
                    window.setTimeout(() => revealFieldErrorTarget(firstServerError), 80);
                } else if (modalScroll) {
                    modalScroll.scrollTop = 0;
                }
            }
        }
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
