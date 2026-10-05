<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/financial-report.css' />" />
<spring:message code="financial.report.trial" var="trialTitle" htmlEscape="true" />
<spring:message code="financial.report.activity" var="activityTitle" htmlEscape="true" />
<spring:message code="financial.report.fullTotals" var="totalsTitle" htmlEscape="true" />
<div class="accounting-workspace financial-report">
  <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="${activityMode ? 'financial.report.activity' : 'financial.report.trial'}" /></h1></div>
  <p><spring:message code="financial.report.basis" /></p>
  <form class="accounting-form financial-filters" method="get" action="<c:url value='${reportRoute}' / data-aws-filter-toolbar>">
    <div class="accounting-grid">
      <label><spring:message code="financial.report.from" /><input type="date" name="from" class="aws-control" value="<c:out value='${parameters.from}' />" required /></label>
      <label><spring:message code="financial.report.through" /><input type="date" name="through" class="aws-control" value="<c:out value='${parameters.through}' />" required /></label>
      <label><spring:message code="financial.report.recordedThrough" /><input type="datetime-local" step="1" name="recordedThrough" class="aws-control" value="<c:out value='${cutoffInput}' />" required /><span><c:out value="${reportZone}" /></span></label>
      <c:if test="${not activityMode}"><sec:authorize access="@access.has(principal, 'FINANCIAL_REPORTS_INSTITUTION')"><label><spring:message code="financial.report.scope" /><select name="institutionWide" class="aws-control"><option value="false" ${not scope.institutionWide ? 'selected' : ''}><spring:message code="financial.report.branch" /></option><option value="true" ${scope.institutionWide ? 'selected' : ''}><spring:message code="financial.report.institution" /></option></select></label></sec:authorize></c:if>
    </div>
    <button class="app-btn btn-primary" type="submit"><spring:message code="financial.report.run" /></button>
  </form>
  <c:if test="${not empty reportError}"><p role="alert"><spring:message code="${reportError}" /></p></c:if>
  <c:if test="${not empty coverage}">
    <sec:authorize access="@access.has(principal, 'FINANCIAL_REPORTS_EXPORT')">
      <form class="accounting-actions" method="get" action="<c:url value='${reportRoute}/export' / data-aws-filter-toolbar>">
        <input type="hidden" name="from" value="<c:out value='${parameters.from}' />" /><input type="hidden" name="through" value="<c:out value='${parameters.through}' />" />
        <input type="hidden" name="recordedThrough" value="<c:out value='${cutoffInput}' />" /><input type="hidden" name="institutionWide" value="<c:out value='${scope.institutionWide}' />" />
        <label><spring:message code="financial.export.format" /><select class="aws-control" name="format"><option value="CSV">CSV</option><option value="XLSX">XLSX</option><option value="PDF">PDF</option></select></label>
        <button class="app-btn btn-neutral" type="submit" data-download-action="true"><spring:message code="financial.export.download" /></button>
      </form>
    </sec:authorize>
    <div class="financial-context"><span><spring:message code="financial.report.draft" /></span><span><spring:message code="financial.report.currency" /></span><span><spring:message code="${scope.institutionWide ? 'financial.report.institution' : 'financial.report.branch'}" /></span></div>
    <c:choose><c:when test="${coverage.complete()}"><p role="status"><spring:message code="financial.report.coverageReviewed" /></p></c:when><c:otherwise><p role="status"><spring:message code="financial.report.coverageIncomplete" /> <spring:message code="financial.report.missingOpenings" />: <c:out value="${coverage.missingOpenings}" />; <spring:message code="financial.report.unbridged" />: <c:out value="${coverage.unbridgedVouchers}" />; <spring:message code="financial.report.uncovered" />: <c:out value="${coverage.uncoveredLoans}" />.</p></c:otherwise></c:choose>
    <p><spring:message code="financial.report.balanceSign" /></p>
  </c:if>
  <c:if test="${not empty trial}">
    <section class="erp-table-wrap" data-aws-table-region aria-label="${trialTitle}"><div class="erp-table-scroll" data-view-position-key="financial-trial"><table class="erp-table"><thead><tr>
      <th><spring:message code="accounting.code" /></th><th><spring:message code="accounting.name" /></th>
      <c:forEach items="${['opening','debit','credit','closing']}" var="label"><th class="financial-number"><spring:message code="financial.report.${label}" /></th></c:forEach>
    </tr></thead><tbody>
      <c:forEach items="${trial.accounts}" var="a"><tr>
        <td><c:url value="/reports/financial/accounts/${a.id}" var="accountUrl"><c:param name="from" value="${parameters.from}" /><c:param name="through" value="${parameters.through}" /><c:param name="recordedThrough" value="${cutoffInput}" /><c:param name="institutionWide" value="${scope.institutionWide}" /></c:url><c:choose><c:when test="${scope.institutionWide}"><c:out value="${a.code}" /></c:when><c:otherwise><a href="<c:out value='${accountUrl}' />"><c:out value="${a.code}" /></a></c:otherwise></c:choose></td><td><c:out value="${a.name}" /></td>
        <c:forEach items="${['opening','debit','credit','closing']}" var="field"><td class="financial-number"><c:choose><c:when test="${a[field] == null}"><spring:message code="financial.report.unknown" /></c:when><c:otherwise><fmt:formatNumber value="${a[field]}" minFractionDigits="2" maxFractionDigits="2" /></c:otherwise></c:choose></td></c:forEach>
      </tr></c:forEach><c:if test="${empty trial.accounts}"><tr><td colspan="6"><spring:message code="financial.report.empty" /></td></tr></c:if>
    </tbody></table></div></section>
    <section class="erp-table-wrap" data-aws-table-region aria-label="${totalsTitle}"><div class="erp-table-scroll erp-table-scroll-sm"><table class="erp-table"><thead><tr><th><spring:message code="financial.report.balance" /></th><th class="financial-number"><spring:message code="accounting.debit" /></th><th class="financial-number"><spring:message code="accounting.credit" /></th></tr></thead><tbody>
      <c:forEach items="${['opening','movement','closing']}" var="phase"><tr><td><spring:message code="financial.report.${phase}" /></td><c:forEach items="${['Debit','Credit']}" var="side"><c:set var="totalKey" value="${phase}${side}" /><td class="financial-number"><c:choose><c:when test="${trial.totals[totalKey] == null}"><spring:message code="financial.report.unknown" /></c:when><c:otherwise><fmt:formatNumber value="${trial.totals[totalKey]}" minFractionDigits="2" maxFractionDigits="2" /></c:otherwise></c:choose></td></c:forEach></tr></c:forEach>
    </tbody></table></div></section>
  </c:if>
  <c:if test="${not empty activity}">
    <h2><c:out value="${activity.code} ${activity.name}" /></h2>
    <div class="financial-context"><c:forEach items="${['opening','closing']}" var="field"><span><spring:message code="financial.report.${field}" />: <c:choose><c:when test="${activity[field] == null}"><spring:message code="financial.report.unknown" /></c:when><c:otherwise><fmt:formatNumber value="${activity[field]}" minFractionDigits="2" maxFractionDigits="2" /></c:otherwise></c:choose></span></c:forEach></div>
    <section class="erp-table-wrap" data-aws-table-region aria-label="${activityTitle}"><div class="erp-table-scroll" data-view-position-key="financial-activity"><table class="erp-table"><thead><tr>
      <c:forEach items="${['effective','recorded','source','reference','evidence','debit','credit']}" var="label"><th><spring:message code="financial.report.${label}" /></th></c:forEach>
    </tr></thead><tbody><c:forEach items="${activity.movements}" var="a"><tr>
      <td><c:out value="${a.effectiveDate}" /></td><td><c:out value="${appTime:format(a.postedAt)}" /></td><td><spring:message code="accounting.source.${a.sourceType}" /></td>
      <td class="financial-evidence"><c:out value="${a.sourceReference}" /><sec:authorize access="@access.has(principal, 'ACCOUNTING_JOURNALS_VIEW')"><a class="app-btn btn-neutral" href="<c:url value='/finance/journals/${a.journalId}' />"><spring:message code="financial.report.source" /></a></sec:authorize><c:if test="${not empty a.reversalOf}"><span><spring:message code="financial.report.correctingEntry" /></span></c:if></td>
      <td class="financial-evidence"><c:out value="${a.evidenceReference}" /></td><td class="financial-number"><fmt:formatNumber value="${a.debit}" minFractionDigits="2" maxFractionDigits="2" /></td><td class="financial-number"><fmt:formatNumber value="${a.credit}" minFractionDigits="2" maxFractionDigits="2" /></td>
    </tr></c:forEach><c:if test="${empty activity.movements}"><tr><td colspan="7"><spring:message code="financial.report.empty" /></td></tr></c:if></tbody></table></div></section>
  </c:if>
  <c:if test="${not empty coverage}"><div class="accounting-actions">
    <c:if test="${parameters.page > 0}"><c:url value="${reportRoute}" var="previousUrl"><c:param name="from" value="${parameters.from}" /><c:param name="through" value="${parameters.through}" /><c:param name="recordedThrough" value="${cutoffInput}" /><c:param name="institutionWide" value="${scope.institutionWide}" /><c:param name="page" value="${parameters.page-1}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${previousUrl}' />"><spring:message code="common.previous" /></a></c:if>
    <c:if test="${hasNext}"><c:url value="${reportRoute}" var="nextUrl"><c:param name="from" value="${parameters.from}" /><c:param name="through" value="${parameters.through}" /><c:param name="recordedThrough" value="${cutoffInput}" /><c:param name="institutionWide" value="${scope.institutionWide}" /><c:param name="page" value="${parameters.page+1}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${nextUrl}' />"><spring:message code="common.next" /></a></c:if>
  </div></c:if>
</div>
<%@ include file="../fragments/footer.jspf" %>
