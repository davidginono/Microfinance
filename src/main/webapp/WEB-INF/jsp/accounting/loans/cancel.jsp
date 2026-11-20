<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %>
<%@ include file="support.jspf" %>
<div class="accounting-workspace accounting-create-workspace" data-resource-form-page="true">
<nav class="erp-page-path"><a href="<c:url value='/finance/loan-records/${cancellation.id}' />"><spring:message code="recording.loanDetails" /></a><span>›</span><span aria-current="page"><spring:message code="cancellation.title" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="cancellation.title" /></h1></div>
<section class="erp-resource-section"><h2><spring:message code="recording.loanDetails" /></h2><div class="erp-panel-body"><dl class="erp-detail-facts">
<div><dt><spring:message code="loan.loanId" /></dt><dd><c:out value="${cancellation.number}" /></dd></div>
<div><dt><spring:message code="recording.client" /></dt><dd><c:out value="${cancellation.client}" /></dd></div>
<div><dt><spring:message code="voucher.status" /></dt><dd><spring:message code="recording.status.${cancellation.status}" /></dd></div>
<div><dt><spring:message code="recording.principal" /> (TZS)</dt><dd><fmt:formatNumber value="${cancellation.principal}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
</dl>
<c:if test="${not empty cancellation.cancelledAt}"><dl class="erp-detail-facts"><div><dt><spring:message code="cancellation.by" /></dt><dd><c:out value="${cancellation.cancelledBy}" /></dd></div><div><dt><spring:message code="voucher.date" /></dt><dd><c:out value="${appTime:display(cancellation.cancelledAt)}" /></dd></div><div><dt><spring:message code="repayment.reason" /></dt><dd><c:out value="${cancellation.reason}" /></dd></div></dl></c:if>
<c:if test="${not empty cancellation.adjustmentId}"><p role="status"><spring:message code="cancellation.request.${cancellation.adjustmentState}" /> <sec:authorize access="@access.has(principal,'LOAN_ADJUSTMENTS_VIEW')"><a class="resource-table-id-link" href="<c:url value='/finance/loan-adjustments/${cancellation.adjustmentId}' />"><spring:message code="common.view" /></a></sec:authorize></p></c:if>
<c:if test="${not empty cancellationAction.blockedReason and cancellation.adjustmentState!='PENDING' and cancellation.adjustmentState!='APPROVED' and empty cancellation.cancelledAt}"><p role="status"><spring:message code="${cancellationAction.blockedReason}" /></p></c:if>
</div></section>
<c:if test="${cancellationAction.available}">
<c:url value="/finance/loan-records/${cancellation.id}/cancel" var="cancelUrl" />
<form:form action="${cancelUrl}" method="post" modelAttribute="cancellationForm" class="accounting-create-form" data-resource-form-page="true" data-workspace-submit="recover">
<form:hidden path="requestKey" /><form:hidden path="state" /><form:hidden path="terms" />
<section class="erp-resource-section"><h2><spring:message code="cancellation.title" /></h2><div class="erp-panel-body">
<p><spring:message code="${cancellation.funded?'cancellation.fundedEffect':'cancellation.effect'}" /></p>
<div class="accounting-grid loan-form-grid">
<div><form:label path="reason"><spring:message code="repayment.reason" /></form:label><form:textarea path="reason" required="required" maxlength="500" cssClass="aws-control" /><form:errors path="reason" cssClass="coa-error" /></div>
<c:if test="${cancellation.funded}">
<div><form:label path="effectiveDate"><spring:message code="voucher.date" /></form:label><form:input path="effectiveDate" type="date" required="required" cssClass="aws-control" /><form:errors path="effectiveDate" cssClass="coa-error" /></div>
<div><form:label path="evidence"><spring:message code="cancellation.fundsEvidence" /></form:label><form:textarea path="evidence" required="required" maxlength="500" cssClass="aws-control" /><form:errors path="evidence" cssClass="coa-error" /></div>
</c:if>
</div>
<label><form:checkbox path="confirmed" required="required" /> <spring:message code="cancellation.confirm" /></label><form:errors path="confirmed" cssClass="coa-error" />
</div></section>
<c:if test="${not empty cancellationError}"><p class="coa-error" role="alert" tabindex="-1"><spring:message code="${cancellationError}" /></p></c:if>
<div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/loan-records/${cancellation.id}' />"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><spring:message code="${cancellationAction.submitLabel}" /></button></div>
</form:form>
</c:if>
<c:if test="${not cancellationAction.available}">
<c:if test="${not empty cancellationError}"><p class="coa-error" role="alert"><spring:message code="${cancellationError}" /></p></c:if>
<div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/loan-records/${cancellation.id}' />"><spring:message code="common.close" /></a></div>
</c:if>
</div><%@ include file="../accounting-form-support.jspf" %><%@ include file="../../fragments/footer.jspf" %>
