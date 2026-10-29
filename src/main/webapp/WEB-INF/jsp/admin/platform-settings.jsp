<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="platform.settings.title" /></h1></div>
<div class="platform-settings-overview">
<section class="erp-panel" id="session-timeout">
<div class="erp-panel-header platform-settings-section-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.session-timeout.title" /></h2>
<sec:authorize access="@access.has(principal, 'PLATFORM_SETTINGS_UPDATE')"><div class="platform-settings-section-actions"><a href="/admin/platform-settings/session-timeout/edit" class="app-btn btn-neutral" aria-label="<spring:message code='platform.settings.session-timeout.edit' />"><spring:message code="common.edit" /></a></div></sec:authorize></div>
<div class="erp-panel-body"><dl class="erp-detail-facts">
<div><dt><spring:message code="platform.settings.timeoutMinutes" /></dt><dd><c:out value="${platformSessionSettings.timeoutMinutes}" default="—" /></dd></div>
</dl></div></section>
<section class="erp-panel" id="support-contact">
<div class="erp-panel-header platform-settings-section-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.support-contact.title" /></h2>
<sec:authorize access="@access.has(principal, 'PLATFORM_SETTINGS_UPDATE')"><div class="platform-settings-section-actions"><a href="/admin/platform-settings/support-contact/edit" class="app-btn btn-neutral" aria-label="<spring:message code='platform.settings.support-contact.edit' />"><spring:message code="common.edit" /></a></div></sec:authorize></div>
<div class="erp-panel-body"><dl class="erp-detail-facts">
<div><dt><spring:message code="platform.settings.displayName" /></dt><dd><c:out value="${supportContactSettings.displayName}" default="—" /></dd></div>
<div><dt><spring:message code="platform.settings.phone" /></dt><dd><c:out value="${supportContactSettings.phone}" default="—" /></dd></div>
<div><dt><spring:message code="platform.settings.emailAddress" /></dt><dd><c:out value="${supportContactSettings.email}" default="—" /></dd></div>
<div><dt><spring:message code="platform.settings.officeHours" /></dt><dd><c:out value="${supportContactSettings.officeHours}" default="—" /></dd></div>
</dl></div></section>
<section class="erp-panel" id="email">
<div class="erp-panel-header platform-settings-section-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.email.title" /></h2>
<sec:authorize access="@access.has(principal, 'PLATFORM_SETTINGS_UPDATE')"><div class="platform-settings-section-actions"><a href="/admin/platform-settings/email/edit" class="app-btn btn-neutral" aria-label="<spring:message code='platform.settings.email.edit' />"><spring:message code="common.edit" /></a><a href="/admin/platform-settings/email/test" class="app-btn btn-neutral"><spring:message code="platform.settings.email.test" /></a></div></sec:authorize></div>
<div class="erp-panel-body"><dl class="erp-detail-facts">
<div><dt><spring:message code="platform.settings.status" /></dt><dd><c:choose><c:when test="${emailDeliveryStatus.configured and emailDeliveryStatus.enabled}"><spring:message code="platform.settings.active" /></c:when><c:when test="${emailDeliveryStatus.configured}"><spring:message code="platform.settings.disabled" /></c:when><c:otherwise><spring:message code="platform.settings.notConfigured" /></c:otherwise></c:choose></dd></div>
<div><dt><spring:message code="platform.settings.host" /></dt><dd><c:out value="${emailDeliveryStatus.host}" default="—" /></dd></div>
<div><dt><spring:message code="platform.settings.fromAddress" /></dt><dd><c:out value="${emailDeliveryStatus.fromAddress}" default="—" /></dd></div>
</dl></div></section>
<section class="erp-panel" id="sms-gateway">
<div class="erp-panel-header platform-settings-section-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.sms-gateway.title" /></h2>
<sec:authorize access="@access.has(principal, 'PLATFORM_SETTINGS_UPDATE')"><div class="platform-settings-section-actions"><a href="/admin/platform-settings/sms-gateway/edit" class="app-btn btn-neutral" aria-label="<spring:message code='platform.settings.sms-gateway.edit' />"><spring:message code="common.edit" /></a><a href="/admin/platform-settings/sms-gateway/test" class="app-btn btn-neutral"><spring:message code="platform.settings.sms-gateway.test" /></a></div></sec:authorize></div>
<div class="erp-panel-body"><dl class="erp-detail-facts">
<div><dt><spring:message code="platform.settings.status" /></dt><dd><c:choose><c:when test="${smsGatewayStatus.configured and smsGatewayStatus.enabled}"><spring:message code="platform.settings.active" /></c:when><c:when test="${smsGatewayStatus.configured}"><spring:message code="platform.settings.disabled" /></c:when><c:otherwise><spring:message code="platform.settings.notConfigured" /></c:otherwise></c:choose></dd></div>
<div><dt><spring:message code="platform.settings.baseUrl" /></dt><dd><c:out value="${smsGatewayStatus.baseUrl}" default="—" /></dd></div>
<div><dt><spring:message code="platform.settings.senderId" /></dt><dd><c:out value="${smsGatewayStatus.senderId}" default="—" /></dd></div>
</dl></div></section>
<section class="erp-panel" id="logo-policy">
<div class="erp-panel-header platform-settings-section-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.logo-policy.title" /></h2>
<sec:authorize access="@access.has(principal, 'PLATFORM_SETTINGS_UPDATE')"><div class="platform-settings-section-actions"><a href="/admin/platform-settings/logo-policy/edit" class="app-btn btn-neutral" aria-label="<spring:message code='platform.settings.logo-policy.edit' />"><spring:message code="common.edit" /></a></div></sec:authorize></div>
<div class="erp-panel-body"><dl class="erp-detail-facts">
<div><dt><spring:message code="platform.settings.minWidth" /></dt><dd><c:out value="${brandingSettings.logoMinWidthPx}" default="—" /></dd></div>
<div><dt><spring:message code="platform.settings.minHeight" /></dt><dd><c:out value="${brandingSettings.logoMinHeightPx}" default="—" /></dd></div>
<div><dt><spring:message code="platform.settings.maxWidth" /></dt><dd><c:out value="${brandingSettings.logoMaxWidthPx}" default="—" /></dd></div>
<div><dt><spring:message code="platform.settings.maxHeight" /></dt><dd><c:out value="${brandingSettings.logoMaxHeightPx}" default="—" /></dd></div>
<div><dt><spring:message code="platform.settings.maxSize" /></dt><dd><c:out value="${brandingSettings.logoMaxFileSizeKb}" default="—" /></dd></div>
</dl></div></section>
<section class="erp-panel" id="loan-top-up"><div class="erp-panel-header platform-settings-section-header"><h2 class="erp-panel-title"><spring:message code="platform.settings.loan-top-up.title" /></h2><a href="/admin/platform-settings/loan-top-up" class="app-btn btn-neutral"><spring:message code="platform.settings.viewInstitutions" /></a></div></section>
</div>
<%@ include file="../fragments/footer.jspf" %>
