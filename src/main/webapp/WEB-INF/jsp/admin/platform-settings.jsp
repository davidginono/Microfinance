<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>


<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">Admin Tools / Platform Settings</p>
    <h1 class="erp-page-title">Platform Settings</h1>
    <p class="erp-page-subtitle">Manage platform-wide session, support, and branding rules for SACCO workspaces.</p>
</div>

<section class="erp-panel aws-settings-panel overflow-hidden">
    <div class="aws-settings-header">
        <p class="aws-settings-kicker">Session</p>
        <h2 class="aws-settings-title">Session Timeout</h2>
        <p class="aws-settings-description">The reminder appears only in the final minute when no activity is detected.</p>
    </div>
    <form action="/admin/platform-settings/session-timeout" method="post" class="erp-panel-body platform-settings-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <div class="aws-settings-subsection">
                <p class="aws-settings-kicker">Inactivity</p>
                <label class="platform-settings-field">
                    Session timeout (minutes)
                    <input name="timeoutMinutes" type="number" min="2" max="480" required
                           class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                           value="${platformSessionSettings.timeoutMinutes}" />
                </label>
            </div>
        </div>
        <div class="platform-settings-actions">
            <p>Choose between 2 and 480 minutes. Active users are refreshed silently before the final warning minute.</p>
            <button type="submit" class="app-btn btn-primary">Save Timeout</button>
        </div>
    </form>
</section>

<section class="erp-panel aws-settings-panel overflow-hidden">
    <div class="aws-settings-header">
        <p class="aws-settings-kicker">Support</p>
        <h2 class="aws-settings-title">Platform Support Contact</h2>
        <p class="aws-settings-description">These details appear in member, SACCO Admin, and staff sidebar support menus.</p>
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
        <div class="platform-settings-actions">
            <p>Save at least a phone number or email address to show this contact in workspace sidebars.</p>
            <button type="submit" class="app-btn btn-primary">Save Contact</button>
        </div>
    </form>
</section>

<section class="erp-panel aws-settings-panel overflow-hidden">
    <div class="aws-settings-header">
        <p class="aws-settings-kicker">Branding</p>
        <h2 class="aws-settings-title">Logo Upload Rules</h2>
        <p class="aws-settings-description">These limits apply to every SACCO logo uploaded from registration or station registry.</p>
    </div>
    <form action="/admin/platform-settings/logo-policy" method="post" class="erp-panel-body platform-settings-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <div class="aws-settings-subsection">
                <p class="aws-settings-kicker">Resolution</p>
                <div class="platform-settings-subgrid">
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
            <div class="aws-settings-subsection">
                <p class="aws-settings-kicker">File Size</p>
                <div>
                    <label class="platform-settings-field">
                        Maximum file size (KB)
                        <input name="maxFileSizeKb" type="number" min="64" max="5120" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${brandingSettings.logoMaxFileSizeKb}" />
                    </label>
                </div>
            </div>
        </div>
        <div class="platform-settings-actions">
            <p>${logoUploadPolicy.helpText}</p>
            <button type="submit" class="app-btn btn-primary">Save Rules</button>
        </div>
    </form>
</section>

<%@ include file="../fragments/footer.jspf" %>
