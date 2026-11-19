<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="layout-support.jspf" %>
<c:set var="layoutFormName" value="layoutNote" /><c:set var="layoutHasErrors" value="false" /><spring:hasBindErrors name="layoutNote"><c:set var="layoutHasErrors" value="true" /></spring:hasBindErrors>
<c:set var="titleKey" value="${layoutNote.index<0?'fs.layout.addNote':'fs.layout.editNote'}" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a href="<c:url value='/reports/financial/statements/layout' />"><spring:message code="fs.layout.title" /></a><span aria-hidden="true">/</span><span aria-current="page"><spring:message code="${titleKey}" /></span></nav>
<div class="accounting-workspace fs-layout-form" data-resource-form-page>
<h1 class="erp-page-title" data-aws-page-header><spring:message code="${titleKey}" /></h1>
<c:url value="/reports/financial/statements/layout/notes" var="saveUrl" />
<form:form modelAttribute="layoutNote" method="post" action="${saveUrl}" cssClass="accounting-create-form" data-layout-note-form="true" data-workspace-submit="recover" data-workspace-field-errors="true" data-unsaved-financial-form="${layoutHasErrors}" data-form-unordered-fields="accounts" data-unchanged-url="${pageContext.request.contextPath}/reports/financial/statements/layout?unchanged=true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><form:hidden path="version" /><form:hidden path="index" /><form:hidden path="requestKey" />
<section class="erp-panel"><div class="erp-panel-header"><h2><spring:message code="fs.note" /></h2></div><div class="erp-panel-body fs-fields">
<label><spring:message code="fs.layout.number" /><form:input path="number" cssClass="aws-control" type="number" min="1" max="999" required="required" /><form:errors path="number" cssClass="coa-error" /></label>
<label><spring:message code="fs.layout.noteTitle" /><form:input path="title" cssClass="aws-control" maxlength="150" required="required" /><form:errors path="title" cssClass="coa-error" /></label>
<label class="fs-full"><spring:message code="fs.layout.text" /><form:textarea path="text" cssClass="aws-control" rows="4" maxlength="5000" data-layout-note-text="true" required="${layoutNote.basis=='NARRATIVE'?'required':null}" /><form:errors path="text" cssClass="coa-error" /></label>
<details class="fs-full fs-layout-translation" ${not empty layoutNote.titleSw or not empty layoutNote.textSw?'open':''}><summary><spring:message code="fs.layout.kiswahili" /></summary><div class="fs-fields"><label class="fs-full"><spring:message code="fs.layout.noteTitle" /><form:input path="titleSw" cssClass="aws-control" maxlength="150" /><form:errors path="titleSw" cssClass="coa-error" /></label><label class="fs-full"><spring:message code="fs.layout.text" /><form:textarea path="textSw" cssClass="aws-control" rows="4" maxlength="5000" /><form:errors path="textSw" cssClass="coa-error" /></label></div></details>
</div></section>
<section class="erp-panel"><div class="erp-panel-header"><h2 id="layout-figures-label"><spring:message code="fs.layout.figures" /></h2></div><div class="erp-panel-body">
<div class="fs-layout-figures" role="radiogroup" aria-labelledby="layout-figures-label"><c:forTokens items="NARRATIVE,BALANCE,MOVEMENT" delims="," var="basis"><label><form:radiobutton path="basis" value="${basis}" /><span><spring:message code="fs.layout.figures.${basis}" /></span></label></c:forTokens></div><form:errors path="basis" cssClass="coa-error" />
<fieldset class="fs-layout-figures-accounts" data-layout-note-accounts ${layoutNote.basis=='NARRATIVE'?'hidden disabled':''}><legend class="sr-only"><spring:message code="fs.layout.accounts" /></legend><%@ include file="layout-accounts.jspf" %>
<label class="fs-layout-check" data-layout-profit ${layoutNote.basis=='BALANCE'?'':'hidden'}><form:checkbox path="includeProfit" disabled="${layoutNote.basis!='BALANCE'}" /><span><spring:message code="fs.layout.addProfit" /></span></label><form:errors path="includeProfit" cssClass="coa-error" />
</fieldset>
</div></section>
<section class="erp-panel"><div class="erp-panel-header"><h2 id="layout-reason-label"><spring:message code="fs.setup.reason" /></h2></div><div class="erp-panel-body"><form:textarea path="reason" cssClass="aws-control" rows="2" maxlength="500" aria-labelledby="layout-reason-label" required="${layoutNote.index<0?'required':null}" /><form:errors path="reason" cssClass="coa-error" /></div></section>
<%@ include file="layout-errors.jspf" %>
<div class="accounting-actions accounting-form-actions" data-form-actions><a class="app-btn btn-neutral" href="<c:url value='/reports/financial/statements/layout' />"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><spring:message code="${layoutNote.index<0?'fs.layout.addNote':'fs.layout.save'}" /></button></div>
</form:form></div>
<%@ include file="../accounting-form-support.jspf" %>
<%@ include file="../../fragments/footer.jspf" %>
