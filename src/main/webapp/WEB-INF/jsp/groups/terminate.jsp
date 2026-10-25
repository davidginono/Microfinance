<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ include file="../fragments/header.jspf" %><%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__link" href="<c:url value='${terminationBack}' />"><spring:message code="group.cycle" /></a><span aria-hidden="true">›</span><span aria-current="page"><spring:message code="${terminationTitle}" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="${terminationTitle}" /></h1></div>
<c:url value="${terminationAction}" var="actionUrl" /><form:form method="post" action="${actionUrl}" modelAttribute="terminationForm" cssClass="accounting-create-form">
<div class="accounting-error-summary" role="alert" tabindex="-1"><form:errors path="*" cssClass="coa-error" element="p" /></div>
<c:if test="${not personalRefusal}"><form:hidden path="expectedVersion" /></c:if>
<section class="accounting-form accounting-create-card"><h2><spring:message code="group.cycle" /></h2><div class="erp-panel-body">
<dl class="erp-detail-facts"><div><dt><spring:message code="group.name" /></dt><dd><c:out value="${terminationGroup}" /></dd></div></dl><p><spring:message code="group.terminationConsequence" /></p>
<label><spring:message code="group.reason" /><form:input path="reason" cssClass="aws-control" maxlength="500" required="required" /></label>
<c:if test="${personalRefusal}"><label class="aws-settings-checkbox-row"><input name="confirmed" type="checkbox" value="true" class="aws-settings-checkbox" required /><span class="aws-settings-checkbox-copy"><spring:message code="group.refusalConfirmation" /></span></label></c:if>
</div></section><div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='${terminationBack}' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch" type="submit"><spring:message code="${terminationTitle}" /></button></div>
</form:form><%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
