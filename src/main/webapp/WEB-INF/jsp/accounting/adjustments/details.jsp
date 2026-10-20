<%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="../loans/support.jspf" %>
<template data-resource-details-page></template>
<nav class="erp-page-path"><a href="<c:url value='/finance/loan-adjustments' />"><spring:message code="adjustment.title" /></a><span>›</span><span aria-current="page"><spring:message code="adjustment.kind.${adjustment.kind}" /></span></nav>
<div class="accounting-workspace"><div class="erp-page-header erp-detail-header"><h1 class="erp-page-title"><spring:message code="adjustment.kind.${adjustment.kind}" /> · <c:out value="${adjustment.number}" /></h1><div class="accounting-actions"><sec:authorize access="@access.has(principal,'LOAN_ADJUSTMENTS_EXPORT')"><a class="app-btn btn-neutral" href="<c:url value='/finance/loan-adjustments/${adjustment.id}/pdf' />"><spring:message code="voucher.pdf" /></a><a class="app-btn btn-neutral" data-print-action href="<c:url value='/finance/loan-adjustments/${adjustment.id}/print' />"><spring:message code="voucher.print" /></a></sec:authorize></div></div>
<c:if test="${not empty formError}"><div class="loan-error-summary" role="alert" tabindex="-1"><spring:message code="${formError}" /></div></c:if>
<section class="accounting-section erp-detail-card"><h2><c:out value="${adjustment.client}" /></h2><dl class="erp-detail-facts">
<div><dt><spring:message code="voucher.status" /></dt><dd><span data-resource-status="${adjustment.state}"><spring:message code="adjustment.state.${adjustment.state}" /></span></dd></div>
<div><dt><spring:message code="voucher.date" /></dt><dd>${appTime:display(adjustment.date)}</dd></div>
<div><dt><spring:message code="voucher.amount" /> (TZS)</dt><dd><fmt:formatNumber value="${adjustment.amount}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
<div><dt><spring:message code="accounting.debit" /></dt><dd><c:out value="${adjustment.debitName}" /></dd></div><div><dt><spring:message code="accounting.credit" /></dt><dd><c:out value="${adjustment.creditName}" /></dd></div>
<div><dt><spring:message code="adjustment.principalChange" /> (TZS)</dt><dd>${adjustment.principalChange}</dd></div><div><dt><spring:message code="adjustment.allowanceChange" /> (TZS)</dt><dd>${adjustment.allowanceChange}</dd></div>
<div><dt><spring:message code="recording.reason" /></dt><dd><c:out value="${adjustment.reason}" /></dd></div><div><dt><spring:message code="adjustment.policyEvidence" /></dt><dd><c:out value="${adjustment.evidence}" /></dd></div>
<div><dt><spring:message code="adjustment.requestedBy" /></dt><dd><c:out value="${adjustment.requester}" /> · ${appTime:display(adjustment.requestedAt)}</dd></div>
<c:if test="${not empty adjustment.decidedAt}"><div><dt><spring:message code="adjustment.reviewedBy" /></dt><dd><c:out value="${adjustment.reviewer}" /> · ${appTime:display(adjustment.decidedAt)}</dd></div><div><dt><spring:message code="recording.reason" /></dt><dd><c:out value="${adjustment.decisionReason}" /></dd></div></c:if></dl>
<p><spring:message code="adjustment.effect.${adjustment.kind}" /></p></section>
<section class="accounting-section erp-detail-card"><h2><spring:message code="recording.balances" /></h2><%@ include file="summary.jspf" %></section>
<c:if test="${adjustment.state=='PENDING'}"><sec:authorize access="@access.has(principal,'LOAN_ADJUSTMENTS_APPROVE')"><section class="accounting-section erp-detail-card"><h2><spring:message code="adjustment.review" /></h2>
<form method="post" action="<c:url value='/finance/loan-adjustments/${adjustment.id}/decision' />" data-loan-form><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="${decisionKey}" />
<div class="accounting-grid"><label><spring:message code="recording.reason" /><textarea class="aws-control" name="reason" maxlength="500" required rows="3"><c:out value="${decisionReason}" /></textarea></label></div>
<p><label><input type="checkbox" name="confirmed" value="true" required /> <spring:message code="adjustment.confirmDecision" /></label></p>
<c:if test="${not independent}"><p><spring:message code="adjustment.error.independent" /></p></c:if>
<div class="accounting-actions"><button class="app-btn btn-neutral" name="approve" value="false" ${independent?'':'disabled'}><spring:message code="adjustment.reject" /></button><button class="app-btn btn-neutral" name="approve" value="true" ${independent?'':'disabled'}><spring:message code="adjustment.approve" /></button></div></form></section></sec:authorize></c:if>
<c:if test="${loan.status=='WRITTEN_OFF'}"><sec:authorize access="@access.has(principal,'LOAN_ADJUSTMENTS_CREATE')"><a class="app-btn btn-neutral" href="<c:url value='/finance/loan-adjustments/new?loanId=${loan.id}&amp;kind=WRITE_BACK' />"><spring:message code="adjustment.kind.WRITE_BACK" /></a></sec:authorize></c:if>
</div><script src="<c:url value='/js/loan-recording.js' />" defer></script><%@ include file="../../fragments/footer.jspf" %>
