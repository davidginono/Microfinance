<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library' />"><spring:message code="library.activities" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><spring:message code="library.editActivity" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="library.editActivity" /></h1></div>
<c:url value="/finance/library/activities/${activity.id}/edit" var="activityEditUrl" />
<form:form method="post" modelAttribute="codeForm" action="${activityEditUrl}" cssClass="accounting-create-form">
    <div class="accounting-error-summary" role="alert" tabindex="-1"><form:errors path="*" cssClass="coa-error" element="p" /></div>
    <fieldset class="accounting-form-section"><legend><spring:message code="library.activityDetails" /></legend><div class="accounting-grid">
        <div><span class="block"><spring:message code="library.code" /></span><strong><c:out value="${activity.code}" /></strong></div>
        <label><spring:message code="library.name" /><form:input path="name" cssClass="aws-control" maxlength="160" required="required" /><form:errors path="name" cssClass="coa-error" /></label>
        <label class="coa-wide"><spring:message code="coa.description" /><form:textarea path="description" cssClass="aws-control" maxlength="500" rows="3" /><form:errors path="description" cssClass="coa-error" /></label>
    </div></fieldset>
    <div class="accounting-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/library' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch"><spring:message code="common.save" /></button></div>
</form:form>
<%@ include file="accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
