<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="layout-support.jspf" %>
<c:set var="layoutFormName" value="layoutRemoval" /><c:set var="layoutHasErrors" value="false" /><spring:hasBindErrors name="layoutRemoval"><c:set var="layoutHasErrors" value="true" /></spring:hasBindErrors>
<c:set var="titleKey" value="${kind=='groups'?'fs.layout.removeGroup':'fs.layout.removeNote'}" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a href="<c:url value='/reports/financial/statements/layout' />"><spring:message code="fs.layout.title" /></a><span aria-hidden="true">/</span><span aria-current="page"><spring:message code="${titleKey}" /></span></nav>
<div class="accounting-workspace fs-layout-form" data-resource-form-page>
<h1 class="erp-page-title" data-aws-page-header><spring:message code="${titleKey}" /></h1>
<c:url value="/reports/financial/statements/layout/${kind}/remove" var="saveUrl" />
<form:form modelAttribute="layoutRemoval" method="post" action="${saveUrl}" cssClass="accounting-create-form" data-workspace-submit="recover" data-workspace-field-errors="true" data-unsaved-financial-form="${layoutHasErrors}">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><form:hidden path="version" /><form:hidden path="index" /><form:hidden path="requestKey" />
<section class="erp-panel"><div class="erp-panel-header"><h2><spring:message code="${kind=='groups'?'fs.layout.groupDetails':'fs.note'}" /></h2></div><div class="erp-panel-body fs-fields">
<c:if test="${not empty removal}"><div><span class="fs-layout-fact-label"><spring:message code="fs.layout.name" /></span><p><c:out value="${removal.name}" /></p></div><div><span class="fs-layout-fact-label"><spring:message code="fs.layout.accounts" /></span><p>${removal.accounts}</p></div></c:if>
<p class="fs-full"><spring:message code="${kind=='groups'?'fs.layout.removeGroupHint':'fs.layout.removeNoteHint'}" /></p>
<label class="fs-full"><spring:message code="fs.setup.reason" /><form:textarea path="reason" cssClass="aws-control" rows="2" maxlength="500" required="required" /><form:errors path="reason" cssClass="coa-error" /></label>
</div></section>
<%@ include file="layout-errors.jspf" %>
<div class="accounting-actions accounting-form-actions" data-form-actions><a class="app-btn btn-neutral" href="<c:url value='/reports/financial/statements/layout' />"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-danger"><spring:message code="fs.layout.remove" /></button></div>
</form:form></div>
<%@ include file="../accounting-form-support.jspf" %>
<%@ include file="../../fragments/footer.jspf" %>
