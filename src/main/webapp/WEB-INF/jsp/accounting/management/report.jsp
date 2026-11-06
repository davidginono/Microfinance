<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/management-reports.css' />" />
<div class="accounting-workspace management-workspace">
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="management.nav.${kind}" /></h1></div>
<c:url value="/reports/financial/operations/${reportName}" var="runUrl" />
<form method="get" action="<c:out value='${runUrl}' />" class="erp-panel management-parameters">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="management.parameters" /></h2></div>
  <c:if test="${not empty formError}"><p class="coa-error" role="alert" tabindex="-1"><spring:message code="${formError}" /></p></c:if>
  <input type="hidden" name="run" value="true" />
  <div class="management-filter-grid">
    <c:if test="${kind!='PORTFOLIO_RISK' and kind!='PORTFOLIO_ANALYSIS'}"><label><spring:message code="${kind=='LOAN_PORTFOLIO'?'management.disbursedFrom':'management.from'}" /><input class="aws-control" name="from" type="date" value="<c:out value='${filter.from}' />" max="${today}" required /></label></c:if>
    <label><spring:message code="${kind=='PORTFOLIO_RISK' or kind=='PORTFOLIO_ANALYSIS'?'management.asAt':(kind=='LOAN_PORTFOLIO'?'management.disbursedThrough':'management.through')}" /><input class="aws-control" name="through" type="date" value="<c:out value='${filter.through}' />" max="${today}" required /></label>
    <c:if test="${kind=='DAILY_COLLECTIONS' or kind=='CASHBOOK'}">
      <label data-loan-choice data-choice-url="<c:url value='/reports/financial/operations/${reportName}/choices/groups' />"><spring:message code="management.group" /><select class="aws-control" name="groupId" data-report-group data-choice-select data-searchable="true"><option value=""><spring:message code="management.allGroups" /></option><c:if test="${not empty groupChoice}"><option selected value="${groupChoice.id}"><c:out value="${groupChoice.getLabel()}" /></option></c:if></select><%@ include file="../loans/choice-search.jspf" %></label>
      <div data-report-accounts data-remove-label="<spring:message code='management.removeAccount' />">
       <c:url value="/reports/financial/operations/${reportName}/choices/accounts" var="accountLookup"><c:param name="groupId" value="${filter.groupId}" /></c:url>
       <label data-loan-choice data-choice-url="<c:out value='${accountLookup}' />"><spring:message code="management.specificAccounts" /><select class="aws-control" data-report-account data-choice-select data-searchable="true"><option value=""><spring:message code="management.chooseAccount" /></option></select><%@ include file="../loans/choice-search.jspf" %></label>
       <ul class="management-account-selection" data-report-selected><c:forEach items="${accountChoices}" var="choice"><li><input type="hidden" name="accountIds" value="${choice.id}" /><span><c:out value="${choice.getLabel()}" /></span><button type="button" class="app-btn btn-neutral" data-remove-account aria-label="<spring:message code='management.removeAccount' />"><spring:message code="common.remove" /></button></li></c:forEach></ul>
       <span role="status" data-report-selection-limit hidden><spring:message code="management.accountLimit" /></span>
      </div>
    </c:if>
    <label data-loan-choice data-choice-url="<c:url value='/reports/financial/operations/${reportName}/choices/clients' />"><spring:message code="management.client" /><select class="aws-control" name="clientId" data-choice-select data-searchable="true"><option value=""><spring:message code="management.allClients" /></option><c:if test="${not empty clientChoice}"><option selected value="${clientChoice.id}"><c:out value="${clientChoice.getLabel()}" /></option></c:if></select><%@ include file="../loans/choice-search.jspf" %></label>
    <c:if test="${kind!='DAILY_COLLECTIONS' and kind!='CASHBOOK'}"><label><spring:message code="management.productSearch" /><input class="aws-control" name="product" maxlength="100" value="<c:out value='${filter.product}' />" /></label></c:if>
  </div><div class="accounting-form-actions"><button class="app-btn btn-neutral"><spring:message code="management.run" /></button></div>
</form>
<c:if test="${not empty report}">
<section class="erp-table-wrap management-preview">
  <div class="app-table-titlebar"><h2><spring:message code="management.preview" /> (<c:out value="${report.count}" />)</h2><div class="accounting-actions">
    <sec:authorize access="@access.has(principal,'FINANCIAL_REPORTS_EXPORT')"><c:forEach items="${['pdf','print']}" var="output"><c:url value="/reports/financial/operations/${reportName}/${output}" var="outputUrl"><c:param name="from" value="${filter.from}" /><c:param name="through" value="${filter.through}" /><c:param name="cutoff" value="${filter.cutoff}" /><c:param name="groupId" value="${filter.groupId}" /><c:forEach items="${filter.accountIds}" var="accountId"><c:param name="accountIds" value="${accountId}" /></c:forEach><c:param name="clientId" value="${filter.clientId}" /><c:param name="product" value="${filter.product}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${outputUrl}' />" ${output=='print'?'target="_blank" rel="noopener"':''}><spring:message code="management.${output}" /></a></c:forEach></sec:authorize>
  </div></div>
  <div class="erp-table-scroll management-canvas"><%@ include file="document.jspf" %></div>
  <div class="management-pager accounting-actions"><span><spring:message code="management.page" arguments="${filter.page+1}" /></span><c:forEach items="${['previous','next']}" var="move"><c:if test="${move=='previous'?filter.page>0:report.hasNext}"><c:url value="/reports/financial/operations/${reportName}" var="pageUrl"><c:param name="run" value="true" /><c:param name="page" value="${filter.page+(move=='previous'?-1:1)}" /><c:param name="from" value="${filter.from}" /><c:param name="through" value="${filter.through}" /><c:param name="cutoff" value="${filter.cutoff}" /><c:param name="groupId" value="${filter.groupId}" /><c:forEach items="${filter.accountIds}" var="accountId"><c:param name="accountIds" value="${accountId}" /></c:forEach><c:param name="clientId" value="${filter.clientId}" /><c:param name="product" value="${filter.product}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${pageUrl}' />"><spring:message code="common.${move}" /></a></c:if></c:forEach></div>
</section>
</c:if>
</div>
<script src="<c:url value='/js/loan-recording.js' />" defer></script>
<script src="<c:url value='/js/management-reports.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>
