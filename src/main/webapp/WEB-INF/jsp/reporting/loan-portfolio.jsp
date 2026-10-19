<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/financial-report.css' />" />
<c:url value="/reports/financial/portfolio/configure" var="configureUrl"><c:param name="from" value="${report.parameters.from}" /><c:param name="through" value="${report.parameters.through}" /><c:param name="recordedThrough" value="${cutoffInput}" /><c:param name="institutionWide" value="${report.scope.institutionWide}" /></c:url>
<div class="accounting-workspace financial-report portfolio-workspace" data-portfolio-report>
  <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />">
    <span class="erp-page-path__item"><spring:message code="finance.nav.reports" /></span><span class="erp-page-path__separator" aria-hidden="true">/</span>
    <a class="erp-page-path__link" href="<c:out value='${configureUrl}' />"><spring:message code="financial.portfolio.menu" /></a><span class="erp-page-path__separator" aria-hidden="true">/</span>
    <span class="erp-page-path__item" aria-current="page"><spring:message code="financial.portfolio.results" /></span>
  </nav>
  <div class="erp-page-header erp-detail-header" data-aws-page-header data-portfolio-report-header>
    <h1 class="erp-page-title"><spring:message code="financial.portfolio.results" /></h1>
    <div class="accounting-actions" data-aws-action-pin="true"><a class="app-btn btn-neutral" href="<c:out value='${configureUrl}' />"><spring:message code="financial.portfolio.changeSettings" /></a></div>
  </div>
  <section class="erp-panel" aria-labelledby="portfolio-context-title">
    <div class="erp-panel-header portfolio-context-header"><h2 class="erp-panel-title" id="portfolio-context-title"><spring:message code="financial.portfolio.reportDetails" /></h2><span class="portfolio-draft"><spring:message code="financial.report.draft" /></span></div>
    <dl class="accounting-context portfolio-context">
      <div><dt><spring:message code="financial.portfolio.period" /></dt><dd><c:out value="${appTime:display(report.parameters.from)}" /> — <c:out value="${appTime:display(report.parameters.through)}" /></dd></div>
      <div><dt><spring:message code="financial.report.scope" /></dt><dd><spring:message code="${report.scope.institutionWide ? 'financial.report.institution' : 'financial.report.branch'}" /></dd></div>
      <div><dt><spring:message code="financial.report.recordedThrough" /></dt><dd><c:out value="${fn:replace(cutoffInput, 'T', ' ')}" /><small><c:out value="${reportZone}" /></small></dd></div>
      <div><dt><spring:message code="financial.portfolio.currency" /></dt><dd>TZS</dd></div>
    </dl>
  </section>
  <c:set var="incompleteCoverage" value="${report.summary.excluded > 0 or report.bookCoverage.missingOpenings > 0 or report.bookCoverage.unbridgedVouchers > 0 or report.bookCoverage.uncoveredLoans > 0}" />
  <section class="portfolio-notice ${incompleteCoverage ? 'portfolio-notice-warning' : ''}" aria-labelledby="portfolio-coverage-title">
    <h2 id="portfolio-coverage-title"><spring:message code="${incompleteCoverage ? 'financial.portfolio.incompleteCoverage' : 'financial.portfolio.coverage'}" /></h2>
    <div class="financial-context"><span><spring:message code="financial.portfolio.covered" />: <strong><c:out value="${report.summary.covered}" /></strong></span><span><spring:message code="financial.portfolio.excluded" />: <strong><c:out value="${report.summary.excluded}" /></strong></span></div>
    <c:if test="${incompleteCoverage}"><p><spring:message code="financial.portfolio.nextStep" /></p></c:if>
    <details><summary><spring:message code="financial.portfolio.coverageDetails" /></summary>
    <p><spring:message code="${incompleteCoverage ? 'financial.portfolio.coverageLimit' : 'financial.portfolio.coveredScope'}" /></p>

      <dl class="portfolio-coverage-details"><div><dt><spring:message code="financial.report.missingOpenings" /></dt><dd><c:out value="${report.bookCoverage.missingOpenings}" /></dd></div><div><dt><spring:message code="financial.report.unbridged" /></dt><dd><c:out value="${report.bookCoverage.unbridgedVouchers}" /></dd></div><div><dt><spring:message code="financial.report.uncovered" /></dt><dd><c:out value="${report.bookCoverage.uncoveredLoans}" /></dd></div></dl>
    </details>
  </section>
  <nav class="portfolio-tabs" aria-label="<spring:message code='financial.portfolio.sections' />" data-portfolio-tabs>
    <a id="portfolio-tab-overview" href="#overview"><spring:message code="financial.portfolio.overview" /></a>
    <a id="portfolio-tab-risk" href="#risk"><spring:message code="financial.portfolio.risk" /></a>
    <a id="portfolio-tab-sources" href="#sources"><spring:message code="${report.scope.institutionWide ? 'financial.portfolio.branches' : 'financial.portfolio.loans'}" /></a>
  </nav>
  <div id="overview" class="portfolio-section" data-portfolio-panel aria-labelledby="portfolio-tab-overview">
<section data-resource-table="reporting-loan-portfolio-1" class="erp-table-wrap" data-aws-table-region aria-labelledby="portfolio-table-0">
    <div class="app-table-titlebar"><div class="app-table-heading"><h2 id="portfolio-table-0"><spring:message code="financial.portfolio.balances" /></h2></div></div><div class="erp-table-scroll erp-table-scroll-sm"><table class="erp-table"><thead><tr><th scope="col"><spring:message code="financial.portfolio.measure" /></th><th scope="col" class="financial-number">TZS</th></tr></thead><tbody>
    <c:forEach items="${['principal','overduePrincipal','overdueInterest','disbursed','collectedPrincipal','collectedInterest']}" var="field"><tr><td><spring:message code="financial.portfolio.${field}" /></td><td class="financial-number"><fmt:formatNumber value="${report.summary[field]}" minFractionDigits="2" maxFractionDigits="2" /></td></tr></c:forEach>
    <c:forEach items="${['fees','recovery']}" var="field"><tr><td><spring:message code="financial.portfolio.${field}" /></td><td><spring:message code="financial.report.unknown" /></td></tr></c:forEach>
    <c:forEach items="${['allowance','writeOff']}" var="field"><tr><td><spring:message code="financial.portfolio.${field}" /></td><td><fmt:formatNumber value="${report.adjustments[field]}" minFractionDigits="2" maxFractionDigits="2" /></td></tr></c:forEach>
  </tbody></table></div><p class="px-4"><spring:message code="adjustment.reportCoverage" /></p></section>
    <details class="portfolio-methodology"><summary><spring:message code="financial.portfolio.basisTitle" /></summary><p><spring:message code="financial.portfolio.basis" /></p><p><spring:message code="financial.portfolio.advanced" /></p></details>
  </div>
  <div id="risk" class="portfolio-section" data-portfolio-panel aria-labelledby="portfolio-tab-risk">
<section data-resource-table="reporting-loan-portfolio-2" class="erp-table-wrap" data-aws-table-region aria-labelledby="portfolio-table-1">
    <div class="app-table-titlebar"><div class="app-table-heading"><h2 id="portfolio-table-1"><spring:message code="financial.portfolio.par" /></h2></div></div><div class="erp-table-scroll erp-table-scroll-sm"><table class="erp-table"><thead><tr><th scope="col"><spring:message code="financial.portfolio.par" /></th><th scope="col" class="financial-number"><spring:message code="financial.portfolio.exposed" /> (TZS)</th><th scope="col" class="financial-number"><spring:message code="financial.portfolio.percentage" /></th></tr></thead><tbody>
    <c:forEach items="${['par1','par30','par90']}" var="field"><tr><td><spring:message code="financial.portfolio.${field}" /></td><td class="financial-number"><fmt:formatNumber value="${report.summary[field]}" minFractionDigits="2" maxFractionDigits="2" /></td><td class="financial-number"><c:choose><c:when test="${report.ratios[field] == null}"><spring:message code="financial.report.unknown" /></c:when><c:otherwise><fmt:formatNumber value="${report.ratios[field]}" minFractionDigits="6" maxFractionDigits="6" />%</c:otherwise></c:choose></td></tr></c:forEach>
  </tbody></table></div></section>
    <details class="portfolio-methodology"><summary><spring:message code="financial.portfolio.metricDefinition" /></summary><p><spring:message code="financial.portfolio.parDefinition" /></p></details>
<section data-resource-table="reporting-loan-portfolio-3" class="erp-table-wrap" data-aws-table-region aria-labelledby="portfolio-table-2">
    <div class="app-table-titlebar"><div class="app-table-heading"><h2 id="portfolio-table-2"><spring:message code="financial.portfolio.ageing" /></h2></div></div><div class="erp-table-scroll erp-table-scroll-sm"><table class="erp-table"><thead><tr><th scope="col"><spring:message code="financial.portfolio.band" /></th><th scope="col"><spring:message code="financial.portfolio.covered" /></th><th scope="col" class="financial-number"><spring:message code="financial.portfolio.principal" /> (TZS)</th></tr></thead><tbody><c:forEach items="${report.ageing}" var="b"><tr><td><spring:message code="financial.portfolio.age.${b.band}" /></td><td><c:out value="${b.loans}" /></td><td class="financial-number"><fmt:formatNumber value="${b.principal}" minFractionDigits="2" maxFractionDigits="2" /></td></tr></c:forEach><c:if test="${empty report.ageing}"><tr><td colspan="3"><spring:message code="financial.portfolio.emptySources" /></td></tr></c:if></tbody></table></div></section>
    <details class="portfolio-methodology"><summary><spring:message code="financial.portfolio.classification" /></summary>
      <p class="portfolio-field-help"><spring:message code="financial.portfolio.regulatoryNote" /></p>
<section data-resource-table="reporting-loan-portfolio-4" class="erp-table-wrap" data-aws-table-region aria-labelledby="portfolio-table-3">
    <div class="app-table-titlebar"><div class="app-table-heading"><h2 id="portfolio-table-3"><spring:message code="financial.portfolio.classification" /></h2></div></div><div class="erp-table-scroll erp-table-scroll-sm"><table class="erp-table"><thead><tr><th scope="col"><spring:message code="financial.portfolio.band" /></th><th scope="col"><spring:message code="financial.portfolio.covered" /></th><th scope="col" class="financial-number"><spring:message code="financial.portfolio.principal" /> (TZS)</th></tr></thead><tbody><c:forEach items="${report.classificationReference}" var="b"><tr><td><spring:message code="financial.portfolio.reg.${b.band}" /></td><td><c:out value="${b.loans}" /></td><td class="financial-number"><fmt:formatNumber value="${b.principal}" minFractionDigits="2" maxFractionDigits="2" /></td></tr></c:forEach><c:if test="${empty report.classificationReference}"><tr><td colspan="3"><spring:message code="financial.portfolio.emptySources" /></td></tr></c:if></tbody></table></div></section>
    </details>
  </div>
  <div id="sources" class="portfolio-section" data-portfolio-panel aria-labelledby="portfolio-tab-sources">
<c:if test="${report.scope.institutionWide}"><section data-resource-table="reporting-loan-portfolio-5" class="erp-table-wrap" data-aws-table-region aria-labelledby="portfolio-table-4">
    <div class="app-table-titlebar"><div class="app-table-heading"><h2 id="portfolio-table-4"><spring:message code="financial.portfolio.branches" /></h2></div></div><div class="erp-table-scroll" data-view-position-key="financial-portfolio-branches"><table class="erp-table"><thead><tr><th scope="col"><spring:message code="financial.report.branch" /></th><th scope="col"><spring:message code="financial.portfolio.covered" /></th><th scope="col"><spring:message code="financial.portfolio.excluded" /></th><th scope="col" class="financial-number"><spring:message code="financial.portfolio.principal" /> (TZS)</th><th scope="col" class="financial-number"><spring:message code="financial.portfolio.par30" /> (TZS)</th><th scope="col" class="financial-number"><spring:message code="financial.portfolio.disbursed" /> (TZS)</th></tr></thead><tbody><c:forEach items="${report.branches}" var="b"><tr><td><c:out value="${b.branch}" /></td><td><c:out value="${b.summary.covered}" /></td><td><c:out value="${b.summary.excluded}" /></td><c:forEach items="${['principal','par30','disbursed']}" var="field"><td class="financial-number"><fmt:formatNumber value="${b.summary[field]}" minFractionDigits="2" maxFractionDigits="2" /></td></c:forEach></tr></c:forEach><c:if test="${empty report.branches}"><tr><td colspan="6"><spring:message code="financial.portfolio.emptySources" /></td></tr></c:if></tbody></table></div></section></c:if>
<c:if test="${not report.scope.institutionWide}"><section data-resource-table="reporting-loan-portfolio-6" class="erp-table-wrap" data-aws-table-region aria-labelledby="portfolio-table-5">
    <div class="app-table-titlebar"><div class="app-table-heading"><h2 id="portfolio-table-5"><spring:message code="financial.portfolio.loans" /></h2></div></div><div class="erp-table-scroll" data-view-position-key="financial-portfolio-loans"><table class="erp-table"><thead><tr><th scope="col"><spring:message code="financial.portfolio.loan" /></th><th scope="col" class="financial-number"><spring:message code="financial.portfolio.principal" /> (TZS)</th><th scope="col" class="financial-number"><spring:message code="financial.portfolio.overduePrincipal" /> (TZS)</th><th scope="col" class="financial-number"><spring:message code="financial.portfolio.overdueInterest" /> (TZS)</th><th scope="col"><spring:message code="financial.portfolio.oldest" /></th><th scope="col"><spring:message code="financial.portfolio.coverage" /></th></tr></thead><tbody><c:forEach items="${report.rows}" var="r"><tr><td><c:out value="${r.number}" /></td><c:forEach items="${['principal','overduePrincipal','overdueInterest']}" var="field"><td class="financial-number"><c:choose><c:when test="${r[field] == null}"><spring:message code="financial.report.unknown" /></c:when><c:otherwise><fmt:formatNumber value="${r[field]}" minFractionDigits="2" maxFractionDigits="2" /></c:otherwise></c:choose></td></c:forEach><td><c:out value="${r.oldest}" /></td><td><spring:message code="${r.complete ? 'financial.portfolio.verified' : 'financial.portfolio.unverified'}" /></td></tr></c:forEach><c:if test="${empty report.rows}"><tr><td colspan="6"><spring:message code="financial.portfolio.emptySources" /></td></tr></c:if></tbody></table></div>
    <div data-table-pagination data-page="${report.parameters.page}" data-table-for="reporting-loan-portfolio-6" class="portfolio-pagination" aria-label="<spring:message code='financial.portfolio.pagination' />">
      <span><spring:message code="financial.portfolio.pageNumber" arguments="${report.parameters.page + 1}" /></span>
      <div class="accounting-actions">
        <c:choose><c:when test="${report.parameters.page > 0}"><c:url value="/reports/financial/portfolio" var="previousUrl"><c:param name="from" value="${report.parameters.from}" /><c:param name="through" value="${report.parameters.through}" /><c:param name="recordedThrough" value="${cutoffInput}" /><c:param name="page" value="${report.parameters.page-1}" /></c:url><a data-page-direction="previous" class="app-btn btn-neutral" href="<c:out value='${previousUrl}' />#sources"><spring:message code="financial.portfolio.previousPage" /></a></c:when><c:otherwise><button data-page-direction="previous" class="app-btn btn-neutral" type="button" disabled><spring:message code="financial.portfolio.previousPage" /></button></c:otherwise></c:choose>
        <c:choose><c:when test="${report.hasNext}"><c:url value="/reports/financial/portfolio" var="nextUrl"><c:param name="from" value="${report.parameters.from}" /><c:param name="through" value="${report.parameters.through}" /><c:param name="recordedThrough" value="${cutoffInput}" /><c:param name="page" value="${report.parameters.page+1}" /></c:url><a data-page-direction="next" class="app-btn btn-neutral" href="<c:out value='${nextUrl}' />#sources"><spring:message code="financial.portfolio.nextPage" /></a></c:when><c:otherwise><button data-page-direction="next" class="app-btn btn-neutral" type="button" disabled><spring:message code="financial.portfolio.nextPage" /></button></c:otherwise></c:choose>
      </div>
    </div>
</section></c:if>
  </div>
  <details class="portfolio-methodology financial-definition"><summary><spring:message code="financial.portfolio.definitions" /></summary><p><c:out value="${report.definitionsVersion}" /> / <c:out value="${report.regulatoryReference}" /></p></details>
</div>
<script src="<c:url value='/js/loan-portfolio-report.js' />" defer></script>
<%@ include file="../fragments/footer.jspf" %>
