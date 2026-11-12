<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="support.jspf" %>
<script src="<c:url value='/js/statement-setup.js' />" defer></script>
<div class="accounting-workspace fs-configure">
<%@ include file="feedback.jspf" %>
<h1 class="erp-page-title" data-aws-page-header><spring:message code="fs.setup" /></h1>
<p class="fs-help"><spring:message code="fs.setup.scope" /></p>

<form method="post" action="<c:url value='/reports/financial/statements/setup' />" data-statement-setup data-workspace-submit>
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="version" value="${form.version}" /><input type="hidden" name="requestKey" value="${form.requestKey}" />
<section class="erp-panel"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="fs.setup.components" /></h2></div><div class="erp-panel-body">
<p id="fs-account-hint" class="fs-help"><spring:message code="fs.setup.accountsHint" /></p>
<div data-setup-list="components" data-max="9"><c:forEach items="${form.components}" var="row" varStatus="vs"><c:set var="rowIndex" value="${vs.index}" /><fieldset class="fs-setup-row"><legend><spring:message code="coa.equityComponent" /></legend><%@ include file="component-fields.jspf" %><button type="button" class="app-btn btn-neutral" data-setup-move="-1"><spring:message code="fs.setup.up" /></button><button type="button" class="app-btn btn-neutral" data-setup-move="1"><spring:message code="fs.setup.down" /></button><button type="button" class="app-btn btn-neutral" data-setup-remove><spring:message code="fs.setup.remove" /></button></fieldset></c:forEach></div>
<button type="button" class="app-btn btn-neutral" data-setup-add="components"><spring:message code="fs.setup.addComponent" /></button></div></section>
<section class="erp-panel"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="fs.setup.categories" /></h2></div><div class="erp-panel-body">
<c:forEach items="${form.categories}" var="category" varStatus="cs"><fieldset class="fs-setup-row"><legend><spring:message code="fs.movement.${category.category}" /></legend><input type="hidden" name="categories[${cs.index}].category" value="${category.category}" /><div class="fs-fields"><label><spring:message code="fs.setup.name" /><input class="aws-control" name="categories[${cs.index}].name" value="<c:out value='${category.name}' />" maxlength="100" /></label><label><spring:message code="fs.setup.nameSw" /><input class="aws-control" name="categories[${cs.index}].nameSw" value="<c:out value='${category.nameSw}' />" maxlength="100" /></label></div></fieldset></c:forEach>
</div></section>
<section class="erp-panel"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="fs.notes" /></h2></div><div class="erp-panel-body">
<div data-setup-list="notes" data-max="30"><c:forEach items="${form.notes}" var="row" varStatus="vs"><c:set var="rowIndex" value="${vs.index}" /><fieldset class="fs-setup-row"><legend><spring:message code="fs.note" /></legend><%@ include file="note-fields.jspf" %><button type="button" class="app-btn btn-neutral" data-setup-remove><spring:message code="fs.setup.remove" /></button></fieldset></c:forEach></div>
<button type="button" class="app-btn btn-neutral" data-setup-add="notes"><spring:message code="fs.setup.addNote" /></button></div></section>
<section class="erp-panel"><div class="erp-panel-body fs-fields"><label class="fs-full"><spring:message code="fs.setup.reason" /><textarea class="aws-control" name="reason" maxlength="500" rows="2" required><c:out value="${form.reason}" /></textarea></label></div></section>
<c:if test="${not empty errorKey}"><div class="accounting-error-summary" role="alert" tabindex="-1"><spring:message code="${errorKey}" /></div></c:if>
<div class="accounting-actions accounting-form-actions"><button class="app-btn btn-launch" type="submit"><spring:message code="fs.setup.save" /></button></div>
</form>
<details class="erp-panel"><summary><spring:message code="fs.setup.accountList" /></summary><div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="bounded-account-reference"><thead><tr><th><spring:message code="fs.account" /></th><th><spring:message code="fs.description" /></th></tr></thead><tbody><c:forEach items="${accounts}" var="a"><tr><td><c:out value="${a.code}" /></td><td><c:out value="${a.name}" /></td></tr></c:forEach></tbody></table></div></details>
<c:set var="row" value="${null}" /><c:set var="rowIndex" value="__INDEX__" />
<template data-setup-template="components"><fieldset class="fs-setup-row"><legend><spring:message code="coa.equityComponent" /></legend><%@ include file="component-fields.jspf" %><button type="button" class="app-btn btn-neutral" data-setup-move="-1"><spring:message code="fs.setup.up" /></button><button type="button" class="app-btn btn-neutral" data-setup-move="1"><spring:message code="fs.setup.down" /></button><button type="button" class="app-btn btn-neutral" data-setup-remove><spring:message code="fs.setup.remove" /></button></fieldset></template>
<template data-setup-template="notes"><fieldset class="fs-setup-row"><legend><spring:message code="fs.note" /></legend><%@ include file="note-fields.jspf" %><button type="button" class="app-btn btn-neutral" data-setup-remove><spring:message code="fs.setup.remove" /></button></fieldset></template>
</div><%@ include file="../../fragments/footer.jspf" %>
