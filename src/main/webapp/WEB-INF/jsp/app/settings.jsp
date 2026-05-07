<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="member.settings.breadcrumb" /></p>
    <h1 class="erp-page-title"><spring:message code="member.settings.title" /></h1>
    <p class="erp-page-subtitle"><spring:message code="member.settings.subtitle" /></p>
</div>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title"><spring:message code="member.settings.language.eyebrow" /></p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="member.settings.language.title" /></h2>
    </div>
    <form action="/app/settings/language" method="post" class="erp-panel-body grid gap-4 md:grid-cols-[minmax(0,24rem)_auto] md:items-end">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="member.settings.language.label" />
            <select name="language" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                <option value="en" ${memberSettingsLanguage eq 'en' ? 'selected' : ''}><spring:message code="member.settings.language.english" /></option>
                <option value="sw" ${memberSettingsLanguage eq 'sw' ? 'selected' : ''}><spring:message code="member.settings.language.swahili" /></option>
            </select>
            <span class="mt-2 block text-sm font-normal normal-case tracking-normal text-slate-500">
                <spring:message code="member.settings.language.help" />
            </span>
        </label>

        <div class="app-modal-actions !justify-start md:justify-end">
            <button type="submit" class="app-btn btn-primary"><spring:message code="member.settings.language.save" /></button>
        </div>
    </form>
</section>

<%@ include file="../fragments/footer.jspf" %>
