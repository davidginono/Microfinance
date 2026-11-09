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
<c:choose>
<c:when test="${empty report}">
<c:url value="/reports/financial/operations/${reportName}" var="runUrl" />
<form method="get" action="<c:out value='${runUrl}' />" class="erp-panel management-parameters">
  <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="management.parameters" /></h2></div>
  <c:if test="${not empty formError}"><p class="coa-error" role="alert" tabindex="-1"><spring:message code="${formError}" /></p></c:if>
  <input type="hidden" name="run" value="true" />
  <div class="management-filter-grid">
    <c:if test="${kind!='PORTFOLIO_RISK' and kind!='PORTFOLIO_ANALYSIS'}"><label><spring:message code="${(kind=='LOAN_PORTFOLIO' or kind=='DISBURSED_LOANS_AUDIT')?'management.disbursedFrom':kind=='EXPECTED_REPAYMENTS'?'management.dueFrom':'management.from'}" /><input class="aws-control" name="from" type="date" value="<c:out value='${filter.from}' />" max="${kind=='EXPECTED_REPAYMENTS'?today.plusDays(366):today}" required /></label></c:if>
    <label><spring:message code="${kind=='PORTFOLIO_RISK' or kind=='PORTFOLIO_ANALYSIS'?'management.asAt':((kind=='LOAN_PORTFOLIO' or kind=='DISBURSED_LOANS_AUDIT')?'management.disbursedThrough':kind=='EXPECTED_REPAYMENTS'?'management.dueThrough':'management.through')}" /><input class="aws-control" name="through" type="date" value="<c:out value='${filter.through}' />" max="${kind=='EXPECTED_REPAYMENTS'?today.plusDays(366):today}" required /></label>
    <c:if test="${kind=='DAILY_COLLECTIONS' or kind=='CASHBOOK'}">
      <c:url value="/reports/financial/operations/${reportName}/choices/accounts" var="accountLookup" />
      <c:forEach items="${['start','end']}" var="edge">
        <c:set var="choice" value="${edge=='start'?startChoice:endChoice}" />
        <label data-loan-choice data-choice-url="<c:out value='${accountLookup}' />">
          <spring:message code="management.${edge}Account" />
          <select class="aws-control" name="${edge}Account" data-choice-select data-searchable="true">
            <option value="" data-empty-option="true"><spring:message code="management.noAccountBound" /></option>
            <c:if test="${not empty choice}"><option selected value="${choice.id}"><c:out value="${choice.label}" /></option></c:if>
          </select>
          <%@ include file="../loans/choice-search.jspf" %>
        </label>
      </c:forEach>
    </c:if>
    <label data-loan-choice data-choice-url="<c:url value='/reports/financial/operations/${reportName}/choices/clients' />"><spring:message code="management.client" /><select class="aws-control" name="clientId" data-choice-select data-searchable="true"><option value="" data-empty-option="true"><spring:message code="management.allClients" /></option><c:if test="${not empty clientChoice}"><option selected value="${clientChoice.id}"><c:out value="${clientChoice.getLabel()}" /></option></c:if></select><%@ include file="../loans/choice-search.jspf" %></label>
    <c:if test="${kind!='DAILY_COLLECTIONS' and kind!='CASHBOOK'}"><label><spring:message code="management.productSearch" /><input class="aws-control" name="product" maxlength="100" value="<c:out value='${filter.product}' />" /></label></c:if>
  </div><div class="accounting-form-actions"><button class="app-btn btn-neutral"><spring:message code="management.run" /></button></div>
</form>
<script src="<c:url value='/js/loan-recording.js' />" defer></script>
<script src="<c:url value='/js/management-reports.js' />" defer></script>
</c:when>
<c:otherwise>
<c:url value="/reports/financial/operations/${reportName}" var="parametersUrl">
  <c:param name="from" value="${filter.from}" /><c:param name="through" value="${filter.through}" />
  <c:param name="startAccount" value="${filter.startAccount}" /><c:param name="endAccount" value="${filter.endAccount}" />
  <c:param name="clientId" value="${filter.clientId}" /><c:param name="product" value="${filter.product}" />
</c:url>
<section class="erp-table-wrap management-preview">
  <div class="app-table-titlebar"><h2 id="management-preview-title"><spring:message code="management.preview" /> <span class="app-table-count">(<c:out value="${report.count}" />)</span></h2><div class="accounting-actions">
    <a class="app-btn btn-neutral" href="<c:out value='${parametersUrl}' />"><spring:message code="management.editParameters" /></a>
    <sec:authorize access="@access.has(principal,'FINANCIAL_REPORTS_EXPORT')"><c:forEach items="${report.hasSpreadsheet() ? ['pdf','print','excel'] : ['pdf','print']}" var="output"><c:url value="/reports/financial/operations/${reportName}/${output}" var="outputUrl"><c:param name="from" value="${filter.from}" /><c:param name="through" value="${filter.through}" /><c:param name="cutoff" value="${filter.cutoff}" /><c:param name="startAccount" value="${filter.startAccount}" /><c:param name="endAccount" value="${filter.endAccount}" /><c:param name="clientId" value="${filter.clientId}" /><c:param name="product" value="${filter.product}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${outputUrl}' />" ${output=='print'?'target="_blank" rel="noopener"':''}><spring:message code="management.${output}" /></a></c:forEach></sec:authorize>
  </div></div>
  <div class="management-canvas" role="region" aria-labelledby="management-preview-title" tabindex="0"><%@ include file="document.jspf" %></div>
  <div class="management-pager accounting-actions"><span><spring:message code="management.page" arguments="${filter.page+1}" /></span><c:forEach items="${['previous','next']}" var="move"><c:if test="${move=='previous'?filter.page>0:report.hasNext}"><c:url value="/reports/financial/operations/${reportName}" var="pageUrl"><c:param name="run" value="true" /><c:param name="page" value="${filter.page+(move=='previous'?-1:1)}" /><c:param name="from" value="${filter.from}" /><c:param name="through" value="${filter.through}" /><c:param name="cutoff" value="${filter.cutoff}" /><c:param name="startAccount" value="${filter.startAccount}" /><c:param name="endAccount" value="${filter.endAccount}" /><c:param name="clientId" value="${filter.clientId}" /><c:param name="product" value="${filter.product}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${pageUrl}' />"><spring:message code="common.${move}" /></a></c:if></c:forEach></div>
</section>
</c:otherwise>
</c:choose>
</div>
<%@ include file="../../fragments/footer.jspf" %>
