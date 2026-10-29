<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:set var="suppressToastMessages" value="true" />
<%@ include file="../fragments/alerts.jspf" %>
<spring:message code="platform.settings.${settingsSection}.test" var="settingsPageTitle" />
<template data-resource-form-page></template>
<nav class="erp-page-path"><a href="/admin/platform-settings"><spring:message code="platform.settings.title" /></a><span>/</span><span aria-current="page"><c:out value="${settingsPageTitle}" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><c:out value="${settingsPageTitle}" /></h1></div>
<form action="/admin/platform-settings/${settingsSection}/test" method="post" data-workspace-success-hash="${settingsSection}" class="platform-create-form">
<section class="erp-panel"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.recipient" /></h2></div><div class="erp-panel-body">
<c:choose>
<c:when test="${settingsSection eq 'email'}">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <label class="platform-settings-field platform-settings-field--wide"><spring:message code="platform.settings.emailRecipient" /><input name="testRecipient" type="email" maxlength="160" required placeholder="admin@example.com"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800" value="${fn:escapeXml(platformSettingsDraft['testRecipient'])}" />
            </label>
        </div>
        </c:when>
<c:when test="${settingsSection eq 'sms-gateway'}">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="platform-settings-grid">
            <label class="platform-settings-field platform-settings-field--wide"><spring:message code="platform.settings.phoneRecipient" /><input name="testPhone" maxlength="40" required placeholder="+255712345678"
                       class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800" value="${fn:escapeXml(platformSettingsDraft['testPhone'])}" />
            </label>
        </div>
        </c:when>
</c:choose>
</div></section>
<c:if test="${not empty error}"><div class="console-message" data-message-type="error" data-workspace-form-error role="alert" tabindex="-1"><c:out value="${error}" /></div></c:if>
<div class="platform-form-actions" data-form-actions><a href="/admin/platform-settings#${settingsSection}" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><c:out value="${settingsPageTitle}" /></button></div>
</form>
<%@ include file="../fragments/footer.jspf" %>
