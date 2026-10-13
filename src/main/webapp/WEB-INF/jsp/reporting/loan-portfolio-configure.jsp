<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/financial-report.css' />" />
<div class="accounting-workspace financial-report portfolio-workspace portfolio-configure">
  <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />">
    <span class="erp-page-path__item"><spring:message code="finance.nav.reports" /></span>
    <span class="erp-page-path__separator" aria-hidden="true">/</span>
    <span class="erp-page-path__item" aria-current="page"><spring:message code="financial.portfolio.menu" /></span>
  </nav>
  <div class="erp-page-header" data-aws-page-header>
    <h1 class="erp-page-title"><spring:message code="financial.portfolio.menu" /></h1>
  </div>
  <c:if test="${not empty formError}">
    <div class="accounting-error-summary" role="alert" id="portfolio-error" tabindex="-1" autofocus>
      <h2><spring:message code="financial.portfolio.correctSettings" /></h2>
      <p class="coa-error"><c:out value="${formError}" /></p>
    </div>
  </c:if>
  <form class="portfolio-setup-form" data-aws-filter-toolbar method="get" action="<c:url value='/reports/financial/portfolio' />">
    <section class="erp-panel" aria-labelledby="portfolio-period-title">
      <div class="erp-panel-header"><h2 class="erp-panel-title" id="portfolio-period-title"><spring:message code="financial.portfolio.period" /></h2></div>
      <div class="erp-panel-body accounting-grid">
        <label for="portfolio-from"><spring:message code="financial.report.from" />
          <input class="aws-control" id="portfolio-from" type="date" name="from" value="<c:out value='${fromInput}' />" max="<c:out value='${todayInput}' />" required aria-describedby="${not empty formError ? 'portfolio-error' : ''}" />
        </label>
        <label for="portfolio-through"><spring:message code="financial.report.through" />
          <input class="aws-control" id="portfolio-through" type="date" name="through" value="<c:out value='${throughInput}' />" max="<c:out value='${todayInput}' />" required aria-describedby="${not empty formError ? 'portfolio-error' : ''}" />
        </label>
      </div>
    </section>
    <section class="erp-panel" aria-labelledby="portfolio-scope-title">
      <div class="erp-panel-header"><h2 class="erp-panel-title" id="portfolio-scope-title"><spring:message code="financial.portfolio.scopeCutoff" /></h2></div>
      <div class="erp-panel-body accounting-grid">
        <sec:authorize access="@access.has(principal, 'FINANCIAL_REPORTS_INSTITUTION')">
          <label for="portfolio-scope"><spring:message code="financial.report.scope" />
            <select class="aws-control" id="portfolio-scope" name="institutionWide"><option value="false" ${not scope.institutionWide ? 'selected' : ''}><spring:message code="financial.report.branch" /></option><option value="true" ${scope.institutionWide ? 'selected' : ''}><spring:message code="financial.report.institution" /></option></select>
          </label>
        </sec:authorize>
        <sec:authorize access="!@access.has(principal, 'FINANCIAL_REPORTS_INSTITUTION')">
          <dl class="portfolio-readonly"><dt><spring:message code="financial.report.scope" /></dt><dd><spring:message code="financial.report.branch" /></dd></dl>
        </sec:authorize>
        <label for="portfolio-cutoff"><spring:message code="financial.report.recordedThrough" />
          <input class="aws-control" id="portfolio-cutoff" type="datetime-local" step="1" name="recordedThrough" value="<c:out value='${cutoffInput}' />" required aria-describedby="${not empty formError ? 'portfolio-error' : ''}" />
          <small><c:out value="${reportZone}" /></small>
        </label>
      </div>
    </section>
    <p class="portfolio-field-help"><spring:message code="financial.portfolio.shortBasis" /></p>
    <div class="accounting-actions accounting-form-actions"><button class="app-btn btn-primary" type="submit"><spring:message code="financial.report.run" /></button></div>
  </form>
</div>
<script src="<c:url value='/js/loan-portfolio-report.js' />" defer></script>
<%@ include file="../fragments/footer.jspf" %>
