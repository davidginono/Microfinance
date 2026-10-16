<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="../loans/support.jspf" %>
<nav class="erp-page-path"><a href="<c:url value='/finance/loan-adjustments' />"><spring:message code="adjustment.title" /></a><span>›</span><span aria-current="page"><spring:message code="adjustment.create" /></span></nav>
<div class="accounting-workspace accounting-create-workspace"><div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="adjustment.create" /></h1></div>
<c:if test="${empty loan and not empty formError}"><div class="loan-error-summary" role="alert" tabindex="-1"><spring:message code="${formError}" /></div></c:if>
<c:choose><c:when test="${empty loan}">
<form method="get" action="<c:url value='/finance/loan-adjustments/new' />" class="accounting-create-form accounting-create-card erp-panel"><section class="accounting-form-section"><div class="accounting-grid">
<label data-loan-choice data-choice-url="<c:url value='/finance/loan-adjustments/choices/loans' />"><spring:message code="recording.loan" /><select class="aws-control" name="loanId" required data-choice-select data-searchable="true"><option value=""><spring:message code="recording.choose" /></option></select><%@ include file="../loans/choice-search.jspf" %></label>
<label><spring:message code="adjustment.column.kind" /><select class="aws-control" name="kind"><c:forEach items="${kinds}" var="k"><option value="${k}" ${adjustmentForm.kind==k?'selected':''}><spring:message code="adjustment.kind.${k}" /></option></c:forEach></select></label></div></section>
<div class="accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/loan-adjustments' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-neutral"><spring:message code="adjustment.continue" /></button></div></form>
</c:when><c:otherwise>
<section class="accounting-section erp-detail-card"><h2><c:out value="${loan.number}" /> · <c:out value="${loan.client}" /></h2><%@ include file="summary.jspf" %></section>
<c:url value="/finance/loan-adjustments" var="saveUrl" /><form:form modelAttribute="adjustmentForm" method="post" action="${saveUrl}" cssClass="accounting-create-form accounting-create-card erp-panel" data-loan-form="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><form:hidden path="requestKey" /><form:hidden path="loanId" /><form:hidden path="kind" />
<c:if test="${not empty formError}"><div class="loan-error-summary" role="alert" tabindex="-1"><spring:message code="${formError}" /><form:errors path="*" /></div></c:if>
<section class="accounting-form-section"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="adjustment.kind.${adjustmentForm.kind}" /></h2></div><div class="accounting-grid">
<label><spring:message code="voucher.date" /><form:input path="date" type="date" min="${loan.disbursed}" max="${today}" required="required" cssClass="aws-control" /></label>
<c:if test="${adjustmentForm.kind=='PROVISION'}">
<label><spring:message code="adjustment.target" /> (TZS)<form:input path="targetAllowance" type="number" min="0" step="0.01" max="${loan.outstanding}" required="required" cssClass="aws-control" /></label>
<c:forEach items="${['allowance','expense']}" var="category"><label data-loan-choice data-choice-url="<c:url value='/finance/loan-adjustments/choices/${category}' />"><spring:message code="adjustment.${category}Account" /><form:select path="${category}AccountId" required="required" cssClass="aws-control" data-choice-select="true" data-searchable="true"><form:option value=""><spring:message code="recording.choose" /></form:option><c:if test="${not empty adjustmentForm[category.concat('AccountId')]}"><form:option value="${adjustmentForm[category.concat('AccountId')]}"><c:out value="${category=='allowance'?selectedAllowance.label:selectedExpense.label}" /></form:option></c:if></form:select><%@ include file="../loans/choice-search.jspf" %></label></c:forEach>
</c:if>
<label><spring:message code="recording.reason" /><form:textarea path="reason" maxlength="500" required="required" cssClass="aws-control" rows="3" /></label>
<label><spring:message code="adjustment.policyEvidence" /><form:textarea path="evidence" maxlength="500" required="required" cssClass="aws-control" rows="3" /></label></div>
<p><spring:message code="adjustment.effect.${adjustmentForm.kind}" /></p>
<label><input type="checkbox" name="confirmed" value="true" required /> <spring:message code="adjustment.confirmRequest" /></label></section>
<div class="accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/loan-adjustments' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-neutral" type="submit"><spring:message code="adjustment.submit" /></button></div>
</form:form></c:otherwise></c:choose></div>
<%@ include file="../accounting-form-support.jspf" %><script src="<c:url value='/js/loan-recording.js' />" defer></script><%@ include file="../../fragments/footer.jspf" %>
