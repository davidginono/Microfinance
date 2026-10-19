<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/payroll' />"><spring:message code="payroll.title" /></a><span aria-hidden="true">›</span><span aria-current="page"><spring:message code="payroll.create.batches" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="payroll.create.batches" /></h1></div>
<div class="accounting-workspace accounting-create-workspace">
<c:url value="/finance/payroll/preview" var="previewAction" />
<form:form modelAttribute="batchForm" method="post" action="${previewAction}" cssClass="accounting-create-form" data-payroll-form="true">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><form:hidden path="requestKey" /><form:hidden path="previewHash" /><form:hidden path="replacesId" />
    <%@ include file="error.jspf" %>
    <section class="erp-resource-section"><h2><spring:message code="payroll.batchSettings" /></h2><div class="grid gap-4">
        <%@ include file="employer-choice.jspf" %>
        <div class="grid gap-4 md:grid-cols-2"><label class="grid gap-2"><spring:message code="payroll.month" /><form:input path="month" type="month" cssClass="aws-control" required="required" /><form:errors path="month" cssClass="coa-error" /></label><label class="grid gap-2"><spring:message code="payroll.deductionDate" /><form:input path="deductionDate" type="date" cssClass="aws-control" required="required" /><form:errors path="deductionDate" cssClass="coa-error" /></label></div>
        <label><form:checkbox path="includeArrears" /> <spring:message code="payroll.includeArrears" /></label>
        <p><spring:message code="payroll.calculationBasis" /></p>
        <c:if test="${batchForm.replacesId!=null}"><p><spring:message code="payroll.replaces" />: <c:out value="PAY-${batchForm.replacesId}" /></p></c:if>
    </div></section>
    <c:if test="${preview!=null}">
        <section class="erp-resource-section"><h2><spring:message code="payroll.preview" /></h2><dl class="erp-detail-facts">
            <div><dt><spring:message code="payroll.employees" /></dt><dd><c:out value="${preview.employees}" /></dd></div><div><dt><spring:message code="payroll.total" /></dt><dd>TZS <fmt:formatNumber value="${preview.total}" minFractionDigits="2" maxFractionDigits="2" /></dd></div><div><dt><spring:message code="payroll.exceptions" /></dt><dd><c:out value="${preview.exceptions}" /></dd></div>
        </dl></section>
        <section class="erp-table-wrap" data-resource-table="payroll-preview" data-table-pagination-mode="external" data-payroll-preview data-page="${previewPage}" data-has-next="${previewHasNext}">
            <div class="app-table-titlebar"><h2><spring:message code="payroll.preview" /></h2></div>
            <c:set var="payrollLines" value="${previewRows}" /><%@ include file="lines.jspf" %>
        </section>
        <c:if test="${preview.exceptions>0}"><label class="block mt-4"><form:checkbox path="acceptExceptions" /> <spring:message code="payroll.acceptExceptions" /></label></c:if>
    </c:if>
    <p class="mt-4"><spring:message code="payroll.requestOnly" /></p>
    <div class="accounting-actions accounting-form-actions">
        <a class="app-btn btn-neutral" href="<c:url value='/finance/payroll' />"><spring:message code="common.cancel" /></a>
        <button type="submit" class="app-btn btn-neutral"><spring:message code="payroll.preview" /></button>
        <c:if test="${preview!=null and preview.total>0}"><button type="submit" class="app-btn btn-launch" formaction="<c:url value='/finance/payroll/freeze' />" data-payroll-freeze><spring:message code="payroll.freeze" /></button></c:if>
    </div>
</form:form></div>
<%@ include file="../accounting-form-support.jspf" %><script src="<c:url value='/js/loan-recording.js' />" defer></script><script src="<c:url value='/js/payroll-deductions.js' />" defer></script><%@ include file="../../fragments/footer.jspf" %>
