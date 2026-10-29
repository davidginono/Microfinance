<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:set var="suppressToastMessages" value="true" />
<%@ include file="../fragments/alerts.jspf" %>
<spring:message code="platform.settings.${settingsSection}.edit" var="settingsPageTitle" />
<template data-resource-form-page></template>
<nav class="erp-page-path"><a href="/admin/platform-settings"><spring:message code="platform.settings.title" /></a><span>/</span><c:if test="${settingsSection eq 'loan-top-up'}"><a href="/admin/platform-settings/loan-top-up"><spring:message code="platform.settings.loan-top-up.title" /></a><span>/</span></c:if><span aria-current="page"><c:out value="${settingsPageTitle}" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><c:out value="${settingsPageTitle}" /></h1></div>
<c:choose>
<c:when test="${settingsSection eq 'session-timeout'}">
<form action="/admin/platform-settings/session-timeout" method="post" data-workspace-success-hash="session-timeout" class="platform-create-form platform-settings-editor" data-unchanged-url="/admin/platform-settings?unchanged=session-timeout#session-timeout">
<section class="erp-panel"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.session-timeout.title" /></h2></div><div class="erp-panel-body">

        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <div class="aws-settings-subsection">
                <label class="platform-settings-field"><spring:message code="platform.settings.timeoutMinutes" /><input name="timeoutMinutes" type="number" min="2" max="480" required
                           class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                           value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['timeoutMinutes']) : platformSessionSettings.timeoutMinutes}" />
                </label>
            </div>
        </div>
        </div></section>
<c:if test="${not empty error}"><div class="console-message" data-message-type="error" data-workspace-form-error role="alert" tabindex="-1"><c:out value="${error}" /></div></c:if>
<div class="platform-form-actions" data-form-actions><a href="/admin/platform-settings#${settingsSection}" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><spring:message code="platform.settings.save" /></button></div>
</form>
</c:when>
<c:when test="${settingsSection eq 'support-contact'}">
<form action="/admin/platform-settings/support-contact" method="post" data-workspace-success-hash="support-contact" class="platform-create-form platform-settings-editor" data-unchanged-url="/admin/platform-settings?unchanged=support-contact#support-contact">
<section class="erp-panel"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.support-contact.title" /></h2></div><div class="erp-panel-body">

        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <label class="platform-settings-field"><spring:message code="platform.settings.displayName" /><input name="displayName" maxlength="120"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['displayName']) : fn:escapeXml(supportContactSettings.displayName)}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.displayRole" /><input name="displayRole" maxlength="120"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['displayRole']) : fn:escapeXml(supportContactSettings.displayRole)}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.phone" /><input name="phone" maxlength="40" placeholder="+255712345678"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['phone']) : fn:escapeXml(supportContactSettings.phone)}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.emailAddress" /><input name="email" type="email" maxlength="160" placeholder="support@example.com"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['email']) : fn:escapeXml(supportContactSettings.email)}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.officeHours" /><input name="officeHours" maxlength="120"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['officeHours']) : fn:escapeXml(supportContactSettings.officeHours)}" />
            </label>
            <label class="platform-settings-field platform-settings-field--wide"><spring:message code="platform.settings.supportNote" /><textarea name="supportNote" rows="4" maxlength="280"
                          class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-3 text-sm text-slate-800"><c:out value="${not empty platformSettingsDraft ? platformSettingsDraft.supportNote : supportContactSettings.supportNote}" /></textarea>
            </label>
        </div>
        </div></section>
<c:if test="${not empty error}"><div class="console-message" data-message-type="error" data-workspace-form-error role="alert" tabindex="-1"><c:out value="${error}" /></div></c:if>
<div class="platform-form-actions" data-form-actions><a href="/admin/platform-settings#${settingsSection}" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><spring:message code="platform.settings.save" /></button></div>
</form>
</c:when>
<c:when test="${settingsSection eq 'email'}">
<form action="/admin/platform-settings/email" method="post" data-workspace-success-hash="email" class="platform-create-form platform-settings-editor" data-unchanged-url="/admin/platform-settings?unchanged=email#email">
<section class="erp-panel"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.email.title" /></h2></div><div class="erp-panel-body">

        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <label class="aws-settings-checkbox-row">
            <input class="aws-settings-checkbox" name="enabled" type="checkbox" value="true" ${(not empty platformSettingsDraft ? platformSettingsDraft['enabled'] eq 'true' : platformEmailSettings.enabled) ? 'checked' : ''} />
            <span class="aws-settings-checkbox-copy">
                <span class="aws-settings-checkbox-title"><spring:message code="platform.settings.enableEmail" /></span>

            </span>
        </label>
        <div class="platform-settings-grid">
            <label class="platform-settings-field"><spring:message code="platform.settings.host" /><input name="host" maxlength="255" placeholder="smtp.example.com"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['host']) : fn:escapeXml(platformEmailSettings.host)}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.port" /><input name="port" type="number" min="1" max="65535" required
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['port']) : platformEmailSettings.port}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.username" /><input name="username" maxlength="160"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['username']) : fn:escapeXml(platformEmailSettings.username)}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.password" /><input aria-describedby="platform-secret-hint" name="password" type="password" autocomplete="new-password"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.fromAddress" /><input name="fromAddress" type="email" maxlength="160" placeholder="no-reply@example.com"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['fromAddress']) : fn:escapeXml(platformEmailSettings.fromAddress)}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.overrideRecipient" /><input name="overrideRecipient" type="email" maxlength="160"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['overrideRecipient']) : fn:escapeXml(platformEmailSettings.overrideRecipient)}" />
            </label>
            <label class="aws-settings-checkbox-row">
                <input class="aws-settings-checkbox" name="sslEnabled" type="checkbox" value="true" ${(not empty platformSettingsDraft ? platformSettingsDraft['sslEnabled'] eq 'true' : platformEmailSettings.sslEnabled) ? 'checked' : ''} />
                <span class="aws-settings-checkbox-copy">
                    <span class="aws-settings-checkbox-title"><spring:message code="platform.settings.ssl" /></span>
                </span>
            </label>
            <label class="aws-settings-checkbox-row">
                <input class="aws-settings-checkbox" name="starttlsEnabled" type="checkbox" value="true" ${(not empty platformSettingsDraft ? platformSettingsDraft['starttlsEnabled'] eq 'true' : platformEmailSettings.starttlsEnabled) ? 'checked' : ''} />
                <span class="aws-settings-checkbox-copy">
                    <span class="aws-settings-checkbox-title"><spring:message code="platform.settings.starttls" /></span>
                </span>
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.connectionTimeoutMs" /><input name="connectionTimeoutMs" type="number" min="1000" max="120000" required
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['connectionTimeoutMs']) : platformEmailSettings.connectionTimeoutMs}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.readTimeoutMs" /><input name="readTimeoutMs" type="number" min="1000" max="120000" required
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['readTimeoutMs']) : platformEmailSettings.readTimeoutMs}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.writeTimeoutMs" /><input name="writeTimeoutMs" type="number" min="1000" max="120000" required
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['writeTimeoutMs']) : platformEmailSettings.writeTimeoutMs}" />
            </label>
        </div>
        <p id="platform-secret-hint" class="aws-settings-help"><spring:message code="platform.settings.keepSecret" /></p>
</div></section>
<c:if test="${not empty error}"><div class="console-message" data-message-type="error" data-workspace-form-error role="alert" tabindex="-1"><c:out value="${error}" /></div></c:if>
<div class="platform-form-actions" data-form-actions><a href="/admin/platform-settings#${settingsSection}" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><spring:message code="platform.settings.save" /></button></div>
</form>
</c:when>
<c:when test="${settingsSection eq 'sms-gateway'}">
<form action="/admin/platform-settings/sms-gateway" method="post" data-workspace-success-hash="sms-gateway" class="platform-create-form platform-settings-editor" data-unchanged-url="/admin/platform-settings?unchanged=sms-gateway#sms-gateway">
<section class="erp-panel"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.sms-gateway.title" /></h2></div><div class="erp-panel-body">

        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <label class="aws-settings-checkbox-row">
            <input class="aws-settings-checkbox" name="enabled" type="checkbox" value="true" ${(not empty platformSettingsDraft ? platformSettingsDraft['enabled'] eq 'true' : platformSmsGatewaySettings.enabled) ? 'checked' : ''} />
            <span class="aws-settings-checkbox-copy">
                <span class="aws-settings-checkbox-title"><spring:message code="platform.settings.enableSms" /></span>

            </span>
        </label>
        <div class="platform-settings-grid">
            <label class="platform-settings-field"><spring:message code="platform.settings.baseUrl" /><input name="baseUrl" maxlength="255" placeholder="https://api.bentergroup.com"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['baseUrl']) : fn:escapeXml(platformSmsGatewaySettings.baseUrl)}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.sendPath" /><input name="sendPath" maxlength="255" placeholder="/version1/messaging/bulk"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['sendPath']) : fn:escapeXml(platformSmsGatewaySettings.sendPath)}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.clientId" /><input name="clientId" maxlength="120"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['clientId']) : fn:escapeXml(platformSmsGatewaySettings.clientId)}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.senderId" /><input name="senderId" maxlength="40"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['senderId']) : fn:escapeXml(platformSmsGatewaySettings.senderId)}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.apiKey" /><input aria-describedby="platform-secret-hint" name="apiKey" type="password" autocomplete="new-password"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.connectTimeoutSeconds" /><input name="connectTimeoutSeconds" type="number" min="1" max="60" required
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['connectTimeoutSeconds']) : platformSmsGatewaySettings.connectTimeoutSeconds}" />
            </label>
            <label class="platform-settings-field"><spring:message code="platform.settings.readTimeoutSeconds" /><input name="readTimeoutSeconds" type="number" min="1" max="60" required
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                       value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['readTimeoutSeconds']) : platformSmsGatewaySettings.readTimeoutSeconds}" />
            </label>
        </div>
        <p id="platform-secret-hint" class="aws-settings-help"><spring:message code="platform.settings.keepSecret" /></p>
</div></section>
<c:if test="${not empty error}"><div class="console-message" data-message-type="error" data-workspace-form-error role="alert" tabindex="-1"><c:out value="${error}" /></div></c:if>
<div class="platform-form-actions" data-form-actions><a href="/admin/platform-settings#${settingsSection}" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><spring:message code="platform.settings.save" /></button></div>
</form>
</c:when>
<c:when test="${settingsSection eq 'logo-policy'}">
<form action="/admin/platform-settings/logo-policy" method="post" data-workspace-success-hash="logo-policy" class="platform-create-form platform-settings-editor" data-unchanged-url="/admin/platform-settings?unchanged=logo-policy#logo-policy">
<section class="erp-panel"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.logo-policy.title" /></h2></div><div class="erp-panel-body">

        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <div class="aws-settings-subsection">
                <div class="platform-settings-subgrid">
                    <label class="platform-settings-field"><spring:message code="platform.settings.minWidth" /><input name="minWidthPx" type="number" min="32" max="4096" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['minWidthPx']) : brandingSettings.logoMinWidthPx}" />
                    </label>
                    <label class="platform-settings-field"><spring:message code="platform.settings.minHeight" /><input name="minHeightPx" type="number" min="32" max="4096" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['minHeightPx']) : brandingSettings.logoMinHeightPx}" />
                    </label>
                    <label class="platform-settings-field"><spring:message code="platform.settings.maxWidth" /><input name="maxWidthPx" type="number" min="32" max="4096" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['maxWidthPx']) : brandingSettings.logoMaxWidthPx}" />
                    </label>
                    <label class="platform-settings-field"><spring:message code="platform.settings.maxHeight" /><input name="maxHeightPx" type="number" min="32" max="4096" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['maxHeightPx']) : brandingSettings.logoMaxHeightPx}" />
                    </label>
                </div>
            </div>
            <div class="aws-settings-subsection">
                <div>
                    <label class="platform-settings-field"><spring:message code="platform.settings.maxSize" /><input name="maxFileSizeKb" type="number" min="64" max="5120" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${not empty platformSettingsDraft ? fn:escapeXml(platformSettingsDraft['maxFileSizeKb']) : brandingSettings.logoMaxFileSizeKb}" />
                    </label>
                </div>
            </div>
        </div>
        </div></section>
<c:if test="${not empty error}"><div class="console-message" data-message-type="error" data-workspace-form-error role="alert" tabindex="-1"><c:out value="${error}" /></div></c:if>
<div class="platform-form-actions" data-form-actions><a href="/admin/platform-settings#${settingsSection}" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><spring:message code="platform.settings.save" /></button></div>
</form>
</c:when>

<c:when test="${settingsSection eq 'loan-top-up'}">
<c:url value="/admin/platform-settings/loan-top-up" var="topUpReturnUrl"><c:param name="q" value="${param.q}" /><c:param name="page" value="${empty param.page ? 0 : param.page}" /><c:param name="size" value="${empty param.size ? 25 : param.size}" /></c:url>
<form action="/admin/platform-settings/sacco-loan-top-up" method="post" class="platform-create-form platform-settings-editor" data-unchanged-url="${fn:escapeXml(topUpReturnUrl)}&amp;unchanged=true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
<input type="hidden" name="returnQuery" value="${fn:escapeXml(param.q)}" />
<input type="hidden" name="returnPage" value="${empty param.page ? 0 : fn:escapeXml(param.page)}" />
<input type="hidden" name="returnSize" value="${empty param.size ? 25 : fn:escapeXml(param.size)}" />
<input type="hidden" name="saccoId" value="${fn:escapeXml(topUpInstitution.saccoId)}" />
<section class="erp-panel"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.loan-top-up.title" /></h2></div><div class="erp-panel-body">
<dl class="erp-detail-facts"><div><dt><spring:message code="common.sacco" /></dt><dd><c:out value="${topUpInstitution.saccoName}" /></dd></div></dl>
<label class="aws-settings-checkbox-row"><input class="aws-settings-checkbox" name="loanTopUpEnabled" type="checkbox" value="true" ${(not empty platformSettingsDraft ? platformSettingsDraft.loanTopUpEnabled eq 'true' : topUpInstitution.loanTopUpEnabled) ? 'checked' : ''} /><span><spring:message code="platform.settings.allowTopUp" /></span></label>
</div></section>
<c:if test="${not empty error}"><div class="console-message" data-message-type="error" data-workspace-form-error role="alert" tabindex="-1"><c:out value="${error}" /></div></c:if>
<div class="platform-form-actions" data-form-actions><a href="${fn:escapeXml(topUpReturnUrl)}" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><spring:message code="platform.settings.save" /></button></div>
</form>
</c:when>
</c:choose>
<%@ include file="../fragments/footer.jspf" %>
