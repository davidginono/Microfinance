<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/payroll/${kind}' />"><spring:message code="payroll.nav.${kind}" /></a><span aria-hidden="true">›</span><span aria-current="page"><spring:message code="payroll.create.${kind}" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="payroll.create.${kind}" /></h1></div>
<div class="accounting-workspace accounting-create-workspace">
<c:choose><c:when test="${kind=='employers'}">
    <form:form modelAttribute="employerForm" method="post" cssClass="accounting-create-form">
        <form:hidden path="requestKey" /><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <%@ include file="error.jspf" %>
        <section class="erp-resource-section"><h2><spring:message code="payroll.employer" /></h2><div class="grid gap-4">
            <label class="grid gap-2" for="employerName"><spring:message code="payroll.employer" /><form:input path="name" id="employerName" cssClass="aws-control" maxlength="160" required="required" /><form:errors path="name" cssClass="coa-error" /></label>
            <label class="grid gap-2" for="agreementReference"><spring:message code="payroll.agreementReference" /><form:input path="agreementReference" id="agreementReference" cssClass="aws-control" maxlength="250" required="required" /><form:errors path="agreementReference" cssClass="coa-error" /></label>
        </div></section>
        <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/payroll/employers' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch"><spring:message code="payroll.create.employers" /></button></div>
    </form:form>
</c:when><c:otherwise>
    <form:form modelAttribute="instructionForm" method="post" cssClass="accounting-create-form">
        <form:hidden path="requestKey" /><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <%@ include file="error.jspf" %>
        <section class="erp-resource-section"><h2><spring:message code="payroll.instruction" /></h2><div class="grid gap-4">
            <%@ include file="employer-choice.jspf" %>
            <div data-loan-choice data-choice-url="<c:url value='/finance/payroll/choices/loans' />">
                <label for="payrollLoan" class="grid gap-2"><spring:message code="payroll.column.loan" /><form:select path="loanId" id="payrollLoan" cssClass="aws-control" data-choice-select="true" required="required"><option value=""><spring:message code="payroll.select" /></option><c:if test="${selectedLoan!=null}"><form:option value="${selectedLoan.id}"><c:out value="${selectedLoan.label}" /></form:option></c:if></form:select><form:errors path="loanId" cssClass="coa-error" /></label>
                <span data-choice-error role="status" data-search-label="<spring:message code='common.search' />" data-error="<spring:message code='payroll.error.lookup' />"></span>
            </div>
            <p><spring:message code="payroll.instructionHelp" /></p>
            <label class="grid gap-2"><spring:message code="payroll.column.limit" /><form:input path="monthlyLimit" type="number" min="0.01" step="0.01" cssClass="aws-control" required="required" /><form:errors path="monthlyLimit" cssClass="coa-error" /></label>
            <div class="grid gap-4 md:grid-cols-2"><label class="grid gap-2"><spring:message code="payroll.column.from" /><form:input path="effectiveFrom" type="date" cssClass="aws-control" required="required" /><form:errors path="effectiveFrom" cssClass="coa-error" /></label><label class="grid gap-2"><spring:message code="payroll.column.to" /><form:input path="effectiveTo" type="date" cssClass="aws-control" required="required" /><form:errors path="effectiveTo" cssClass="coa-error" /></label></div>
            <label class="grid gap-2"><spring:message code="payroll.evidence" /><form:textarea path="evidence" cssClass="aws-control" maxlength="500" rows="3" required="required" /><form:errors path="evidence" cssClass="coa-error" /></label>
        </div></section>
        <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/payroll/instructions' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch"><spring:message code="payroll.create.instructions" /></button></div>
    </form:form>
</c:otherwise></c:choose>
</div>
<%@ include file="../accounting-form-support.jspf" %><script src="<c:url value='/js/loan-recording.js' />" defer></script><%@ include file="../../fragments/footer.jspf" %>
