<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="layout-support.jspf" %>
<c:set var="layoutFormName" value="layoutMovementRow" />
<c:set var="layoutHasErrors" value="false" /><spring:hasBindErrors name="layoutMovementRow"><c:set var="layoutHasErrors" value="true" /></spring:hasBindErrors>
<c:set var="titleKey" value="${layoutMovementRow.index<0?'fs.layout.addMovementRow':'fs.layout.editMovementRow'}" />
<c:url value="/reports/financial/statements/layout" var="layoutUrl" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a href="${layoutUrl}"><spring:message code="fs.layout.title" /></a><span aria-hidden="true">/</span><span aria-current="page"><spring:message code="${titleKey}" /></span></nav>
<div class="accounting-workspace fs-layout-form" data-resource-form-page>
<h1 class="erp-page-title" data-aws-page-header><spring:message code="${titleKey}" /></h1>
<c:url value="/reports/financial/statements/layout/movement-rows" var="saveUrl" />
<form:form modelAttribute="layoutMovementRow" method="post" action="${saveUrl}" cssClass="accounting-create-form" data-workspace-submit="recover" data-workspace-field-errors="true" data-unsaved-financial-form="${layoutHasErrors}" data-unchanged-url="${layoutUrl}?unchanged=true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><form:hidden path="version" /><form:hidden path="index" /><form:hidden path="requestKey" />
<section class="erp-panel"><div class="erp-panel-header"><h2><spring:message code="fs.layout.movementRow" /></h2></div>
<div class="erp-panel-body fs-fields">
<label><spring:message code="fs.layout.name" /><form:input path="name" cssClass="aws-control" maxlength="100" required="required" /><form:errors path="name" cssClass="coa-error" /></label>
<label><spring:message code="fs.setup.category" /><form:select path="category" cssClass="aws-control" required="required"><form:option value=""><spring:message code="voucher.equity.choose" /></form:option><c:forEach items="${movementCategories}" var="category"><form:option value="${category}"><spring:message code="fs.movement.${category}" /></form:option></c:forEach></form:select><form:errors path="category" cssClass="coa-error" /></label>
<details class="fs-full fs-layout-translation" ${not empty layoutMovementRow.nameSw?'open':''}><summary><spring:message code="fs.layout.kiswahili" /></summary><label><spring:message code="fs.layout.name" /><form:input path="nameSw" cssClass="aws-control" maxlength="100" /><form:errors path="nameSw" cssClass="coa-error" /></label></details>
<label class="fs-full"><spring:message code="fs.setup.reason" /><form:textarea path="reason" cssClass="aws-control" rows="2" maxlength="500" required="${layoutMovementRow.index<0?'required':null}" /><form:errors path="reason" cssClass="coa-error" /></label>
</div></section>
<%@ include file="layout-errors.jspf" %>
<div class="accounting-actions accounting-form-actions" data-form-actions><a class="app-btn btn-neutral" href="${layoutUrl}"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><spring:message code="${layoutMovementRow.index<0?'fs.layout.addMovementRow':'fs.layout.save'}" /></button></div>
</form:form></div>
<%@ include file="../accounting-form-support.jspf" %>
<%@ include file="../../fragments/footer.jspf" %>
