<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="layout-support.jspf" %>
<c:set var="layoutFormName" value="layoutGroup" /><c:set var="layoutHasErrors" value="false" /><spring:hasBindErrors name="layoutGroup"><c:set var="layoutHasErrors" value="true" /></spring:hasBindErrors>
<c:set var="titleKey" value="${layoutGroup.index<0?'fs.layout.addGroup':'fs.layout.editGroup'}" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a href="<c:url value='/reports/financial/statements/layout' />"><spring:message code="fs.layout.title" /></a><span aria-hidden="true">/</span><span aria-current="page"><spring:message code="${titleKey}" /></span></nav>
<div class="accounting-workspace fs-layout-form" data-resource-form-page>
<h1 class="erp-page-title" data-aws-page-header><spring:message code="${titleKey}" /></h1>
<c:url value="/reports/financial/statements/layout/groups" var="saveUrl" />
<form:form modelAttribute="layoutGroup" method="post" action="${saveUrl}" cssClass="accounting-create-form" data-workspace-submit="recover" data-workspace-field-errors="true" data-unsaved-financial-form="${layoutHasErrors}" data-unchanged-url="${pageContext.request.contextPath}/reports/financial/statements/layout?unchanged=true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><form:hidden path="version" /><form:hidden path="index" /><form:hidden path="requestKey" />
<section class="erp-panel"><div class="erp-panel-header"><h2><spring:message code="fs.layout.groupDetails" /></h2></div><div class="erp-panel-body fs-fields">
<label><spring:message code="fs.layout.name" /><form:input path="name" cssClass="aws-control" maxlength="100" required="required" /><form:errors path="name" cssClass="coa-error" /></label>
<label><spring:message code="fs.layout.position" /><form:select path="position" cssClass="aws-control"><c:forEach begin="1" end="${positions}" var="position"><form:option value="${position}" /></c:forEach></form:select><form:errors path="position" cssClass="coa-error" /></label>
<details class="fs-full fs-layout-translation" ${not empty layoutGroup.nameSw?'open':''}><summary><spring:message code="fs.layout.kiswahili" /></summary><label><spring:message code="fs.layout.name" /><form:input path="nameSw" cssClass="aws-control" maxlength="100" /><form:errors path="nameSw" cssClass="coa-error" /></label></details>
</div></section>
<section class="erp-panel"><div class="erp-panel-header"><h2><spring:message code="fs.layout.accounts" /></h2></div><div class="erp-panel-body">
<%@ include file="layout-accounts.jspf" %>
<label class="fs-layout-check"><form:checkbox path="retained" /><span><spring:message code="fs.layout.addProfit" /></span></label><form:errors path="retained" cssClass="coa-error" />
</div></section>
<section class="erp-panel"><div class="erp-panel-header"><h2 id="layout-reason-label"><spring:message code="fs.setup.reason" /></h2></div><div class="erp-panel-body"><form:textarea path="reason" cssClass="aws-control" rows="2" maxlength="500" aria-labelledby="layout-reason-label" required="${layoutGroup.index<0?'required':null}" /><form:errors path="reason" cssClass="coa-error" /></div></section>
<%@ include file="layout-errors.jspf" %>
<div class="accounting-actions accounting-form-actions" data-form-actions><a class="app-btn btn-neutral" href="<c:url value='/reports/financial/statements/layout' />"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><spring:message code="${layoutGroup.index<0?'fs.layout.addGroup':'fs.layout.save'}" /></button></div>
</form:form></div>
<%@ include file="../accounting-form-support.jspf" %>
<%@ include file="../../fragments/footer.jspf" %>
