<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/sl-gl-reconciliation.css' />" />
<div class="accounting-workspace reconciliation-workspace">
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="reconciliation.title" /></h1></div>
<c:if test="${not empty formError}"><p class="coa-error" role="alert" tabindex="-1"><spring:message code="${formError}" /></p></c:if>
<section class="erp-table-wrap" data-resource-table="sl-gl-reconciliation" data-aws-table-region>
  <div class="app-table-titlebar"><h2><spring:message code="reconciliation.discrepancies" /><c:if test="${not empty report}"> <span class="app-table-count">(<c:out value="${report.count}" />)</span></c:if></h2>
    <div class="accounting-actions resource-table-actions">
      <c:if test="${not empty report}"><c:if test="${canExportReport}">
        <c:forEach items="${['pdf','print']}" var="output"><c:url value="/reports/financial/sl-gl-reconciliation/${output}" var="outputUrl"><c:param name="from" value="${filter.from}" /><c:param name="through" value="${filter.through}" /><c:param name="cutoff" value="${filter.cutoff}" /><c:param name="component" value="${filter.component}" /><c:param name="activityId" value="${filter.activityFilter}" /></c:url>
          <a class="app-btn btn-neutral" href="<c:out value='${outputUrl}' />" ${output=='print'?'target="_blank" rel="noopener"':''}><spring:message code="management.${output}" /></a>
        </c:forEach>
      </c:if></c:if>
    </div>
  </div>
  <form method="get" action="<c:url value='/reports/financial/sl-gl-reconciliation' />" class="resource-table-filters" data-reconciliation-query data-aws-filter-toolbar>
    <input type="hidden" name="run" value="true" /><input type="hidden" name="cutoff" value="<c:out value='${filter.cutoff}' />" />
    <label><spring:message code="reconciliation.check" /><select class="aws-control" name="component"><c:forEach items="${['ALL','PRINCIPAL','ALLOWANCE','INTEREST_COLLECTIONS','INTEREST_RECEIVABLE']}" var="check"><option value="${check}" ${filter.component==check?'selected':''}><spring:message code="reconciliation.component.${check}" /></option></c:forEach><c:if test="${canViewGroupAttribution}"><option value="GROUP_ATTRIBUTION" ${filter.component=='GROUP_ATTRIBUTION'?'selected':''}><spring:message code="reconciliation.component.GROUP_ATTRIBUTION" /></option></c:if></select></label>
    <label><spring:message code="management.from" /><input class="aws-control" type="date" name="from" value="<c:out value='${filter.from}' />" max="${today}" required /></label>
    <label><spring:message code="management.through" /><input class="aws-control" type="date" name="through" value="<c:out value='${filter.through}' />" max="${today}" required /></label>
    <label data-loan-choice data-category="activities" data-choice-url="${pageContext.request.contextPath}/reports/financial/sl-gl-reconciliation/activities"><spring:message code="reconciliation.activity" />
      <select class="aws-control" name="activityId" data-choice-select data-searchable="true"><option value="" data-empty-option="true"><spring:message code="coa.all" /></option>
        <option value="UNASSIGNED" data-choice-pinned="true" ${filter.unassigned?'selected':''}><spring:message code="reconciliation.unassigned" /></option>
        <c:if test="${not empty selectedActivity}"><option value="${selectedActivity.id}" selected><c:out value="${pageContext.response.locale.language=='sw' and not empty selectedActivity.nameSw?selectedActivity.nameSw:selectedActivity.name}" /></option></c:if>
        <c:forEach items="${activities}" var="activity" end="24"><c:if test="${activity.id!=filter.activityId}"><option value="${activity.id}"><c:out value="${pageContext.response.locale.language=='sw' and not empty activity.nameSw?activity.nameSw:activity.name}" /></option></c:if></c:forEach>
      </select><span class="coa-error" role="status" data-choice-error data-error="<spring:message code='recording.lookupError' />" data-search-label="<spring:message code='reconciliation.activity' />"></span>
    </label>
    <div class="resource-table-filter-actions"><button class="app-btn btn-neutral"><spring:message code="management.run" /></button><a class="app-btn btn-neutral" href="<c:url value='/reports/financial/sl-gl-reconciliation' />"><spring:message code="common.clear" /></a></div>
  </form>
  <div class="reconciliation-canvas">
    <c:choose><c:when test="${not empty report}"><%@ include file="document.jspf" %></c:when><c:otherwise>
      <div class="erp-table-scroll"><table data-table-sort-mode="none" data-table-sort-reason="retired-report" class="erp-table reconciliation-table"><thead><tr><c:forEach items="${['client','account','reference','date','transaction','description','slDebit','slCredit','glDebit','glCredit','debitVariance','creditVariance']}" var="column"><th scope="col" data-table-identifier><spring:message code="reconciliation.column.${column}" /></th></c:forEach></tr></thead><tbody><tr><td colspan="12" class="reconciliation-empty"><spring:message code="reconciliation.choosePeriod" /></td></tr></tbody></table></div>
    </c:otherwise></c:choose>
  </div>
  <div data-table-pagination data-page="${filter.page}" data-table-for="sl-gl-reconciliation">
    <c:forEach items="${['previous','next']}" var="move"><c:if test="${not empty report and (move=='previous'?filter.page>0:report.hasNext)}">
      <c:url value="/reports/financial/sl-gl-reconciliation" var="pageUrl"><c:param name="run" value="true" /><c:param name="page" value="${filter.page+(move=='previous'?-1:1)}" /><c:param name="from" value="${filter.from}" /><c:param name="through" value="${filter.through}" /><c:param name="cutoff" value="${filter.cutoff}" /><c:param name="component" value="${filter.component}" /><c:param name="activityId" value="${filter.activityFilter}" /></c:url>
      <a data-page-direction="${move}" href="<c:out value='${pageUrl}' />"><spring:message code="common.${move}" /></a>
    </c:if></c:forEach>
  </div>
</section>
<c:if test="${not empty report}"><div class="reconciliation-basis reconciliation-screen-note"><p><spring:message code="reconciliation.screenNote" /></p></div></c:if>
</div>
<script src="<c:url value='/js/loan-recording.js' />" defer></script><script src="<c:url value='/js/sl-gl-reconciliation.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>
