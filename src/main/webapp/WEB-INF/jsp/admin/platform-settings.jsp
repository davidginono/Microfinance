<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>


<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">Admin Tools / Platform Settings</p>
    <h1 class="erp-page-title">Platform Settings</h1>
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
        <p class="aws-settings-kicker">Delivery</p>
        <h2 class="aws-settings-title">Email Delivery</h2>
        <p class="aws-settings-description">Configure platform SMTP settings for OTP codes, notifications, and invitations. Leave password blank to keep the saved value.</p>
    </div>
    <form action="/admin/platform-settings/email" method="post" class="erp-panel-body platform-settings-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <label class="platform-settings-field platform-settings-field--wide settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                <input name="enabled" type="checkbox" value="true" class="mt-1" ${platformEmailSettings.enabled ? 'checked' : ''} />
                <span>
                    <span class="block font-semibold text-slate-900">Enable email delivery</span>
                    <span class="mt-1 block text-xs text-slate-500">
                        Status:
                        <c:choose>
                            <c:when test="${emailDeliveryStatus.configured}">Configured<c:if test="${emailDeliveryStatus.enabled}"> and active</c:if><c:if test="${not emailDeliveryStatus.enabled}"> but disabled</c:if></c:when>
                            <c:otherwise>Not configured</c:otherwise>
                        </c:choose>
                    </span>
                </span>
            </label>
            <label class="platform-settings-field">
                SMTP host
                <input name="host" maxlength="255" placeholder="smtp.example.com"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(platformEmailSettings.host)}" />
            </label>
            <label class="platform-settings-field">
                SMTP port
                <input name="port" type="number" min="1" max="65535" required
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${platformEmailSettings.port}" />
            </label>
            <label class="platform-settings-field">
                SMTP username
                <input name="username" maxlength="160"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(platformEmailSettings.username)}" />
            </label>
            <label class="platform-settings-field">
                SMTP password
                <input name="password" type="password" autocomplete="new-password" placeholder="${emailDeliveryStatus.passwordConfigured ? 'Saved password on file' : 'Enter SMTP password'}"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800" />
            </label>
            <label class="platform-settings-field">
                From address
                <input name="fromAddress" type="email" maxlength="160" placeholder="no-reply@example.com"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(platformEmailSettings.fromAddress)}" />
            </label>
            <label class="platform-settings-field">
                Override recipient
                <input name="overrideRecipient" type="email" maxlength="160" placeholder="Optional staging inbox"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(platformEmailSettings.overrideRecipient)}" />
            </label>
            <label class="platform-settings-field settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                <input name="sslEnabled" type="checkbox" value="true" class="mt-1" ${platformEmailSettings.sslEnabled ? 'checked' : ''} />
                <span>
                    <span class="block font-semibold text-slate-900">Use SSL</span>
                </span>
            </label>
            <label class="platform-settings-field settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                <input name="starttlsEnabled" type="checkbox" value="true" class="mt-1" ${platformEmailSettings.starttlsEnabled ? 'checked' : ''} />
                <span>
                    <span class="block font-semibold text-slate-900">Use STARTTLS</span>
                </span>
            </label>
            <label class="platform-settings-field">
                Connection timeout (ms)
                <input name="connectionTimeoutMs" type="number" min="1000" max="120000" required
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${platformEmailSettings.connectionTimeoutMs}" />
            </label>
            <label class="platform-settings-field">
                Read timeout (ms)
                <input name="readTimeoutMs" type="number" min="1000" max="120000" required
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${platformEmailSettings.readTimeoutMs}" />
            </label>
            <label class="platform-settings-field">
                Write timeout (ms)
                <input name="writeTimeoutMs" type="number" min="1000" max="120000" required
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${platformEmailSettings.writeTimeoutMs}" />
            </label>
        </div>
        <div class="platform-settings-actions">
            <p>Leave the password blank to keep the saved value. New passwords are stored encrypted.</p>
            <button type="submit" class="app-btn btn-primary">Save Email Settings</button>
        </div>
    </form>
    <form action="/admin/platform-settings/email/test" method="post" class="erp-panel-body platform-settings-form border-t border-slate-200">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <label class="platform-settings-field platform-settings-field--wide">
                Send test email to
                <input name="testRecipient" type="email" maxlength="160" required placeholder="admin@example.com"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800" />
            </label>
        </div>
        <div class="platform-settings-actions">
            <p>Uses the current saved or environment email configuration.</p>
            <button type="submit" class="app-btn btn-neutral">Send Test Email</button>
        </div>
    </form>
</section>

<section class="erp-panel aws-settings-panel overflow-hidden">
    <div class="aws-settings-header">
        <p class="aws-settings-kicker">Delivery</p>
        <h2 class="aws-settings-title">SMS Gateway</h2>
        <p class="aws-settings-description">Configure the Benter Group SMS gateway used for OTP codes and alerts. Leave API key blank to keep the saved value.</p>
    </div>
    <form action="/admin/platform-settings/sms-gateway" method="post" class="erp-panel-body platform-settings-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <label class="platform-settings-field platform-settings-field--wide settings-checkbox-card flex items-start gap-3 rounded border border-slate-200 bg-white px-4 py-4 text-sm text-slate-700">
                <input name="enabled" type="checkbox" value="true" class="mt-1" ${platformSmsGatewaySettings.enabled ? 'checked' : ''} />
                <span>
                    <span class="block font-semibold text-slate-900">Enable SMS gateway</span>
                    <span class="mt-1 block text-xs text-slate-500">
                        Status:
                        <c:choose>
                            <c:when test="${smsGatewayStatus.configured}">Configured<c:if test="${smsGatewayStatus.enabled}"> and active</c:if><c:if test="${not smsGatewayStatus.enabled}"> but disabled</c:if></c:when>
                            <c:otherwise>Not configured</c:otherwise>
                        </c:choose>
                    </span>
                </span>
            </label>
            <label class="platform-settings-field">
                Base URL
                <input name="baseUrl" maxlength="255" placeholder="https://api.bentergroup.com"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(platformSmsGatewaySettings.baseUrl)}" />
            </label>
            <label class="platform-settings-field">
                Send path
                <input name="sendPath" maxlength="255" placeholder="/version2/messaging/legacy"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(platformSmsGatewaySettings.sendPath)}" />
            </label>
            <label class="platform-settings-field">
                Client ID
                <input name="clientId" maxlength="120"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(platformSmsGatewaySettings.clientId)}" />
            </label>
            <label class="platform-settings-field">
                Sender ID
                <input name="senderId" maxlength="40"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${fn:escapeXml(platformSmsGatewaySettings.senderId)}" />
            </label>
            <label class="platform-settings-field">
                API key
                <input name="apiKey" type="password" autocomplete="new-password" placeholder="${smsGatewayStatus.apiKeyConfigured ? 'Saved API key on file' : 'Enter API key'}"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800" />
            </label>
            <label class="platform-settings-field">
                Connect timeout (seconds)
                <input name="connectTimeoutSeconds" type="number" min="1" max="60" required
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${platformSmsGatewaySettings.connectTimeoutSeconds}" />
            </label>
            <label class="platform-settings-field">
                Read timeout (seconds)
                <input name="readTimeoutSeconds" type="number" min="1" max="60" required
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${platformSmsGatewaySettings.readTimeoutSeconds}" />
            </label>
        </div>
        <div class="platform-settings-actions">
            <p>Station SMS balances and warning thresholds remain on the SMS Usage page.</p>
            <button type="submit" class="app-btn btn-primary">Save SMS Gateway</button>
        </div>
    </form>
    <form action="/admin/platform-settings/sms-gateway/test" method="post" class="erp-panel-body platform-settings-form border-t border-slate-200">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <label class="platform-settings-field platform-settings-field--wide">
                Send test SMS to
                <input name="testPhone" maxlength="40" required placeholder="Example: +255 746 359 369"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800" />
            </label>
        </div>
        <div class="platform-settings-actions">
            <p>Uses the current saved or environment SMS gateway configuration.</p>
            <button type="submit" class="app-btn btn-neutral">Send Test SMS</button>
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
