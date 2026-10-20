<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<template data-resource-details-page></template><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/payroll/${kind}' />"><spring:message code="payroll.nav.${kind}" /></a><span aria-hidden="true">›</span><span aria-current="page"><spring:message code="payroll.details" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="payroll.details" /></h1></div>
<div class="accounting-workspace">
<%@ include file="error.jspf" %>
<section class="erp-resource-section"><h2><spring:message code="payroll.nav.${kind}" /></h2><dl class="erp-detail-facts">
<c:choose><c:when test="${kind=='employers'}">
    <div><dt><spring:message code="payroll.employer" /></dt><dd><c:out value="${employer.name}" /></dd></div><div><dt><spring:message code="payroll.agreementReference" /></dt><dd><c:out value="${employer.agreementReference}" /></dd></div>
</c:when><c:otherwise>
    <div><dt><spring:message code="payroll.employer" /></dt><dd><c:out value="${instruction.employer}" /></dd></div><div><dt><spring:message code="payroll.column.client" /></dt><dd><c:out value="${instruction.client}" /></dd></div><div><dt><spring:message code="payroll.employeeNumber" /></dt><dd><c:out value="${instruction.employeeNumber}" /></dd></div>
    <div><dt><spring:message code="payroll.column.loan" /></dt><dd><c:out value="${instruction.loanNumber}" /></dd></div><div><dt><spring:message code="payroll.column.limit" /></dt><dd><fmt:formatNumber value="${instruction.monthlyLimit}" minFractionDigits="2" maxFractionDigits="2" /></dd></div><div><dt><spring:message code="payroll.state" /></dt><dd><spring:message code="payroll.instruction.${instruction.stopped?'stopped':'active'}" /></dd></div>
    <div><dt><spring:message code="payroll.column.from" /></dt><dd><c:out value="${appTime:date(instruction.effectiveFrom)}" /></dd></div><div><dt><spring:message code="payroll.column.to" /></dt><dd><c:out value="${appTime:date(instruction.effectiveTo)}" /></dd></div><div><dt><spring:message code="payroll.evidence" /></dt><dd><c:out value="${instruction.evidence}" /></dd></div>
</c:otherwise></c:choose>
</dl></section>
<c:if test="${kind=='instructions' and not instruction.stopped}"><sec:authorize access="@access.has(principal,'PAYROLL_DEDUCTIONS_UPDATE')">
    <form action="<c:url value='/finance/payroll/instructions/${instruction.id}/stop' />" method="post" class="accounting-create-form">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <section class="erp-resource-section"><h2><spring:message code="payroll.stopInstruction" /></h2><label class="grid gap-2"><spring:message code="payroll.reason" /><textarea class="aws-control" name="reason" required maxlength="500" rows="3"></textarea></label><p><spring:message code="payroll.stopHelp" /></p></section>
        <div class="accounting-actions accounting-form-actions"><button class="app-btn btn-neutral"><spring:message code="payroll.stopInstruction" /></button></div>
    </form><%@ include file="../accounting-form-support.jspf" %>
</sec:authorize></c:if>
</div><%@ include file="../../fragments/footer.jspf" %>
