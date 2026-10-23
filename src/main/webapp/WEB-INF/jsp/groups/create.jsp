<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ include file="../fragments/header.jspf" %><%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/groups' />"><spring:message code="group.title" /></a><span aria-hidden="true">›</span><span aria-current="page"><spring:message code="group.create" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="group.create" /></h1></div>
<div class="accounting-workspace accounting-create-workspace"><c:url value="/finance/groups" var="saveUrl" />
<form:form modelAttribute="groupForm" action="${saveUrl}" method="post" cssClass="accounting-create-form"><form:hidden path="requestKey" />
<div class="accounting-error-summary" role="alert" tabindex="-1"><form:errors path="*" element="p" cssClass="coa-error" /></div>
<section class="accounting-form accounting-create-card"><h2><spring:message code="group.details" /></h2><div class="accounting-grid">
<label><spring:message code="group.name" /><form:input path="name" cssClass="aws-control" maxlength="160" required="required" /></label>
<label><spring:message code="group.legalForm" /><form:select path="legalForm" cssClass="aws-control"><c:forEach items="${['INFORMAL','REGISTERED_GROUP','ORGANIZATION']}" var="kind"><form:option value="${kind}"><spring:message code="group.legalForm.${kind}" /></form:option></c:forEach></form:select></label>
<label><spring:message code="group.evidence" /><form:input path="evidence" cssClass="aws-control" maxlength="500" required="required" /></label>
</div></section><div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/groups' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch" type="submit"><spring:message code="group.create" /></button></div>
</form:form></div><%@ include file="../accounting/accounting-form-support.jspf" %><%@ include file="../fragments/footer.jspf" %>
