<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<style>
    .platform-settings-form {
        max-width: 68rem;
    }

    .platform-settings-grid {
        display: grid;
        max-width: 62rem;
        grid-template-columns: repeat(auto-fit, minmax(min(100%, 21rem), 28rem));
        gap: 1rem;
        align-items: start;
    }

    .platform-settings-card {
        max-width: 62rem;
    }

    .platform-settings-subgrid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(min(100%, 13rem), 1fr));
        gap: 0.85rem;
    }

    .platform-settings-field {
        display: block;
        max-width: 28rem;
        color: #334155;
        font-size: 0.875rem;
        font-weight: 600;
        line-height: 1.35;
    }

    .platform-settings-field--wide {
        grid-column: 1 / -1;
        max-width: 58rem;
    }

    .platform-settings-field input,
    .platform-settings-field textarea {
        box-sizing: border-box;
        width: 100%;
        min-height: 3.15rem;
        margin-top: 0.4rem;
        border: 1px solid #cbd5e1;
        border-radius: 0.375rem;
        background: #ffffff;
        padding: 0.78rem 0.95rem;
        color: #1e293b;
        font-size: 0.875rem;
        font-weight: 500;
        line-height: 1.35;
        box-shadow: inset 0 1px 2px rgba(15, 23, 42, 0.04);
        transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }

    .platform-settings-field input::placeholder,
    .platform-settings-field textarea::placeholder {
        color: #94a3b8;
    }

    .platform-settings-field input:focus,
    .platform-settings-field textarea:focus {
        border-color: #0284c7;
        outline: none;
        box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.14);
    }

    .platform-settings-field textarea {
        min-height: 8.75rem;
        resize: vertical;
    }

    .platform-settings-actions {
        max-width: 62rem;
    }

    @media (max-width: 640px) {
        .platform-settings-form,
        .platform-settings-grid,
        .platform-settings-card,
        .platform-settings-field,
        .platform-settings-field--wide,
        .platform-settings-actions {
            max-width: none;
        }
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Platform Settings</p>
    <h1 class="erp-page-title">Platform Settings</h1>
    <p class="erp-page-subtitle">Manage platform-wide support contact and branding rules for SACCO workspaces.</p>
</div>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title">Support</p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink">Platform Support Contact</h2>
        <p class="mt-1 text-sm text-slate-500">These details appear in member, SACCO Admin, and staff sidebar support menus.</p>
    </div>
    <form action="/admin/platform-settings/support-contact" method="post" class="erp-panel-body platform-settings-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <label class="platform-settings-field">
                Display name
                <input name="displayName" maxlength="120"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(supportContactSettings.displayName)}" />
            </label>
            <label class="platform-settings-field">
                Role or desk
                <input name="displayRole" maxlength="120"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(supportContactSettings.displayRole)}" />
            </label>
            <label class="platform-settings-field">
                Phone number
                <input name="phone" maxlength="40" placeholder="Example: +255 746 359 369"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(supportContactSettings.phone)}" />
            </label>
            <label class="platform-settings-field">
                Email address
                <input name="email" type="email" maxlength="160" placeholder="support@example.com"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(supportContactSettings.email)}" />
            </label>
            <label class="platform-settings-field">
                Office hours
                <input name="officeHours" maxlength="120" placeholder="Example: Monday to Friday, 08:00 - 17:00"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(supportContactSettings.officeHours)}" />
            </label>
            <label class="platform-settings-field platform-settings-field--wide">
                Support note
                <textarea name="supportNote" rows="4" maxlength="280"
                          class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-3 text-sm text-slate-800"><c:out value="${supportContactSettings.supportNote}" /></textarea>
            </label>
        </div>
        <div class="platform-settings-actions mt-5 relative flex flex-row items-center justify-between gap-3 flex-wrap border-t border-slate-200 pt-5">
            <p class="min-w-[16rem] flex-[1_1_24rem] text-sm text-slate-500">Save at least a phone number or email address to show this contact in workspace sidebars.</p>
            <button type="submit" class="app-btn btn-primary shrink-0 min-w-[10rem]" style="height:3rem;">Save Contact</button>
        </div>
    </form>
</section>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title">Branding</p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink">Logo Upload Rules</h2>
        <p class="mt-1 text-sm text-slate-500">These limits apply to every SACCO logo uploaded from registration or station registry.</p>
    </div>
    <form action="/admin/platform-settings/logo-policy" method="post" class="erp-panel-body platform-settings-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <div class="platform-settings-card rounded-md border border-slate-200 bg-slate-50 p-4">
                <p class="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">Resolution</p>
                <div class="platform-settings-subgrid mt-3">
                    <label class="platform-settings-field">
                        Minimum width
                        <input name="minWidthPx" type="number" min="32" max="4096" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${brandingSettings.logoMinWidthPx}" />
                    </label>
                    <label class="platform-settings-field">
                        Minimum height
                        <input name="minHeightPx" type="number" min="32" max="4096" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${brandingSettings.logoMinHeightPx}" />
                    </label>
                    <label class="platform-settings-field">
                        Maximum width
                        <input name="maxWidthPx" type="number" min="32" max="4096" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${brandingSettings.logoMaxWidthPx}" />
                    </label>
                    <label class="platform-settings-field">
                        Maximum height
                        <input name="maxHeightPx" type="number" min="32" max="4096" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${brandingSettings.logoMaxHeightPx}" />
                    </label>
                </div>
            </div>
            <div class="platform-settings-card rounded-md border border-slate-200 bg-slate-50 p-4">
                <p class="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">File Size</p>
                <div class="mt-3">
                    <label class="platform-settings-field">
                        Maximum file size (KB)
                        <input name="maxFileSizeKb" type="number" min="64" max="5120" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${brandingSettings.logoMaxFileSizeKb}" />
                    </label>
                </div>
            </div>
        </div>
        <div class="platform-settings-actions mt-5 relative flex flex-row items-center justify-between gap-3 flex-wrap border-t border-slate-200 pt-5">
            <p class="min-w-[16rem] flex-[1_1_24rem] text-sm text-slate-500">${logoUploadPolicy.helpText}</p>
            <button type="submit" class="app-btn btn-primary shrink-0 min-w-[10rem]" style="height:3rem;">Save Rules</button>
        </div>
    </form>
</section>

<%@ include file="../fragments/footer.jspf" %>
