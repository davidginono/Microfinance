<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="manager.settings.breadcrumb" text="Manager Panel / Settings" /></p>
    <h1 class="erp-page-title"><spring:message code="manager.settings.title" text="Manager Settings" /></h1>
    <p class="erp-page-subtitle"><spring:message code="manager.settings.subtitle" text="Maintain the guarantor, board, and language defaults used across your SACCO review workflow." /></p>
</div>
<form method="post" action="/manager/settings" class="erp-form-wrap max-w-2xl space-y-3">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    <label class="block text-sm font-medium text-slate-700"><spring:message code="settings.requiredGuarantors" text="Required Guarantors" /></label>
    <input type="number" class="w-full rounded-lg border border-slate-300 px-3 py-2" name="requiredGuarantors" value="${settings.requiredGuarantors}" />
    <label class="block text-sm font-medium text-slate-700"><spring:message code="settings.boardSize" text="Board Size" /></label>
    <input type="number" class="w-full rounded-lg border border-slate-300 px-3 py-2" name="boardSize" value="${settings.boardSize}" />
    <label class="block text-sm font-medium text-slate-700"><spring:message code="settings.boardQuorum" text="Board Quorum" /></label>
    <input type="number" class="w-full rounded-lg border border-slate-300 px-3 py-2" name="boardQuorum" value="${settings.boardQuorum}" />
    <label class="block text-sm font-medium text-slate-700"><spring:message code="settings.defaultLanguage" text="Default Language" /></label>
    <select name="defaultLanguage" class="w-full rounded-lg border border-slate-300 px-3 py-2">
        <option value="en" ${settings.defaultLanguage == 'en' ? 'selected' : ''}><spring:message code="language.english" text="English" /></option>
        <option value="sw" ${settings.defaultLanguage == 'sw' ? 'selected' : ''}><spring:message code="language.swahili" text="Kiswahili" /></option>
    </select>
    <button class="app-btn btn-primary"><spring:message code="common.save" text="Save" /></button>
</form>

<%@ include file="../fragments/footer.jspf" %>

