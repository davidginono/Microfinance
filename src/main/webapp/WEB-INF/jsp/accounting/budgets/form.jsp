<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/management-reports.css' />" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a href="<c:url value='/finance/budgets' />"><spring:message code="management.budgets" /></a><span aria-hidden="true">/</span><span aria-current="page"><spring:message code="${empty budgetForm.replacesId?'management.budget.create':'management.budget.revise'}" /></span></nav>
<div class="accounting-workspace accounting-create-workspace">
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="${empty budgetForm.replacesId?'management.budget.create':'management.budget.revise'}" /></h1></div>
<c:url value="/finance/budgets" var="saveUrl" />
<form:form modelAttribute="budgetForm" method="post" action="${saveUrl}" cssClass="accounting-create-form budget-create-form">
  <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><form:hidden path="requestKey" /><form:hidden path="replacesId" />
  <c:if test="${not empty formError}"><div class="accounting-error-summary" role="alert" tabindex="-1"><p class="coa-error"><spring:message code="${formError}" /></p><form:errors path="*" /></div></c:if>
  <section class="accounting-section"><h2><spring:message code="management.budget.details" /></h2>
  <div class="accounting-grid">
    <label><spring:message code="management.date" /><form:input path="date" type="date" max="${today}" required="required" cssClass="aws-control" /></label>
    <label><spring:message code="management.year" /><form:input path="year" type="number" min="1900" max="2200" required="required" readonly="${not empty budgetForm.replacesId}" cssClass="aws-control" /></label>
    <label data-loan-choice data-choice-url="<c:url value='/finance/budgets/accounts' />"><spring:message code="management.account" /><form:select path="accountId" required="required" cssClass="aws-control" data-choice-select="true" data-searchable="true"><form:option value=""><spring:message code="management.chooseAccount" /></form:option><c:if test="${not empty selectedAccount}"><form:option value="${selectedAccount.id}"><c:out value="${selectedAccount.name}" /></form:option></c:if></form:select><%@ include file="../loans/choice-search.jspf" %></label>
  </div></section>
  <section class="accounting-section budget-months"><h2 id="budget-months-title"><spring:message code="management.budget.months" /> (TZS)</h2>
  <div class="erp-table-scroll"><table data-table-sort-mode="none" data-table-sort-reason="editor" class="erp-table" aria-labelledby="budget-months-title"><thead><tr><th scope="col"><spring:message code="management.month" /></th><th scope="col"><spring:message code="management.amount" /> (TZS)</th></tr></thead><tbody>
  <c:forEach begin="0" end="11" var="month"><tr><td><label for="budget-month-${month}"><spring:message code="management.month.${month+1}" /></label></td><td><form:input path="amounts[${month}]" id="budget-month-${month}" type="number" data-amount-input="true" min="0" max="9999999999999999.99" step="0.01" required="required" cssClass="aws-control" /></td></tr></c:forEach>
  </tbody></table></div></section>
  <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/budgets' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch" type="submit"><spring:message code="management.budget.save" /></button></div>
</form:form>
</div>
<%@ include file="../accounting-form-support.jspf" %>
<script src="<c:url value='/js/loan-recording.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>
