<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<style>
    .staff-settings-action-bar {
        display: grid;
        gap: 0.75rem;
        border: 1px solid #e2e8f0;
        border-radius: 0.4rem;
        background: #f8fafc;
        padding: 0.75rem;
    }
    .staff-settings-action-row {
        display: flex;
        flex-wrap: wrap;
        align-items: flex-end;
        gap: 0.75rem;
    }
    .staff-settings-action-field {
        flex: 1 1 22rem;
        min-width: 0;
    }
    .staff-settings-action-bar select,
    .staff-settings-action-button {
        box-sizing: border-box;
        height: 3.5rem;
        min-height: 3.5rem;
    }
    .staff-settings-action-button {
        flex: 0 0 auto;
        min-width: 11rem;
        justify-content: center;
        padding-top: 0;
        padding-bottom: 0;
    }
    @media (max-width: 639px) {
        .staff-settings-action-button {
            flex-basis: 100%;
        }
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="staff.settings.breadcrumb" text="Staff Workspace / Settings" /></p>
    <h1 class="erp-page-title"><spring:message code="staff.settings.title" text="Settings" /></h1>
    <p class="erp-page-subtitle"><spring:message code="staff.settings.subtitle" text="Manage your personal language preference." /></p>
</div>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title"><spring:message code="member.settings.language.eyebrow" /></p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="member.settings.language.title" /></h2>
    </div>
    <form action="/staff/settings/language" method="post" class="erp-panel-body">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="staff-settings-action-bar">
            <div class="staff-settings-action-row">
                <label class="staff-settings-action-field block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <spring:message code="staff.settings.language.label" text="Staff Workspace Language" />
                    <select name="language" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm text-slate-800">
                        <option value="en" ${staffSettingsLanguage eq 'en' ? 'selected' : ''}><spring:message code="member.settings.language.english" /></option>
                        <option value="sw" ${staffSettingsLanguage eq 'sw' ? 'selected' : ''}><spring:message code="member.settings.language.swahili" /></option>
                    </select>
                </label>
                <button type="submit" class="staff-settings-action-button app-btn btn-primary"><spring:message code="member.settings.language.save" /></button>
            </div>
            <span class="block text-sm font-normal text-slate-500">
                <spring:message code="staff.settings.language.help" text="Translated labels and shared staff navigation will switch immediately after you save." />
            </span>
        </div>
    </form>
</section>

<%@ include file="../fragments/footer.jspf" %>
