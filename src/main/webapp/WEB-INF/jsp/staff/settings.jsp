<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>


<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="staff.settings.breadcrumb" text="Staff Workspace / Settings" /></p>
    <h1 class="erp-page-title"><spring:message code="staff.settings.title" text="Settings" /></h1>
    <p class="erp-page-subtitle"><spring:message code="staff.settings.subtitle" text="Manage your personal language preference." /></p>
</div>

<section class="erp-panel aws-settings-panel overflow-hidden">
    <div class="aws-settings-header">
        <p class="aws-settings-kicker"><spring:message code="member.settings.language.eyebrow" /></p>
        <h2 class="aws-settings-title"><spring:message code="member.settings.language.title" /></h2>
    </div>
    <form action="/staff/settings/language" method="post" class="aws-settings-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="aws-settings-control-row">
            <label class="aws-settings-field">
                <spring:message code="staff.settings.language.label" text="Staff Workspace Language" />
                <select name="language" class="aws-control">
                    <option value="en" ${staffSettingsLanguage eq 'en' ? 'selected' : ''}><spring:message code="member.settings.language.english" /></option>
                    <option value="sw" ${staffSettingsLanguage eq 'sw' ? 'selected' : ''}><spring:message code="member.settings.language.swahili" /></option>
                </select>
            </label>
            <p class="aws-settings-help">
                <spring:message code="staff.settings.language.help" text="Translated labels and shared staff navigation will switch immediately after you save." />
            </p>
        </div>
        <div class="aws-settings-footer">
            <button type="submit" class="app-btn btn-primary"><spring:message code="member.settings.language.save" /></button>
        </div>
    </form>
</section>

<%@ include file="../fragments/footer.jspf" %>
