<%@ taglib prefix="report" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="support.jspf" %>
<%@ include file="preview-urls.jspf" %>
<div class="accounting-workspace">
 <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a data-fs-parent href="<c:url value='${kind=="LEDGER"?"/reports/financial/statements/ledger":"/reports/financial/statements"}' />"><spring:message code="${kind=='LEDGER'?'fs.title.LEDGER':'fs.workspace'}" /></a><span>/</span><span aria-current="page"><spring:message code="fs.previewTitle" text="Reports Preview" /></span></nav>
 <div class="erp-page-header erp-detail-header" data-aws-page-header>
  <h1 class="erp-page-title"><spring:message code="fs.previewTitle" text="Reports Preview" /></h1>
  <sec:authorize access="@access.has(principal, 'FINANCIAL_REPORTS_EXPORT')"><form method="get" action="<c:url value='${base}/export' />" data-fs-export class="report-page-actions" data-aws-action-pin="true">
   <input type="hidden" name="kind" value="${kind}" /><c:if test="${not empty selectionKind}"><input type="hidden" name="selectionKind" value="${selectionKind}" /></c:if><c:if test="${not empty line}"><input type="hidden" name="line" value="<c:out value='${line}' />" /></c:if><c:if test="${not empty account}"><input type="hidden" name="account" value="${account}" /></c:if>
   <a class="app-btn btn-neutral" href="<c:out value='${printUrl}' />" data-print-action="true"><report:reportExport format="print" /></a>
   <button type="submit" class="app-btn btn-neutral" name="format" value="PDF" data-download-action="true"><report:reportExport format="pdf" /></button>
   <button type="submit" class="app-btn btn-neutral" name="format" value="XLSX" data-download-action="true"><report:reportExport format="excel" /></button>
  </form></sec:authorize>
 </div>
 <div class="fs-workspace" data-tab-loading="<spring:message code='fs.tabLoading' />" data-tab-error="<spring:message code='fs.tabError' />">
 <div class="fs-tabs" role="navigation" aria-label="<spring:message code='fs.statement' />"><c:forEach items="${kinds}" var="k"><c:url value="${base}" var="tabUrl"><c:param name="kind" value="${k}" /></c:url><a data-fs-tab="${k}" data-no-page-preloader="true" href="<c:out value='${tabUrl}' />" ${kind==k?'aria-current="page"':''}><spring:message code="fs.short.${k}" /></a></c:forEach></div>
 <div class="fs-toolbar">
  <%@ include file="../report-preview-search.jspf" %>
  <details class="fs-view-settings"><summary><spring:message code="fs.viewSettings" /></summary><div class="fs-view-fields">
   <label class="fs-select-setting"><span><spring:message code="fs.font" /></span><select class="aws-control" data-fs-font><option value="reference"><spring:message code="fs.referenceFont" /></option><option value="sans">Arial</option><option value="serif">Times New Roman</option><option value="mono">Courier New</option></select></label>
   <label class="fs-select-setting"><span><spring:message code="fs.density" /></span><select class="aws-control" data-fs-density><option value="compact"><spring:message code="fs.compact" /></option><option value="comfortable"><spring:message code="fs.comfortable" /></option></select></label>
   <label data-fs-comparison-setting ${kind=='POSITION' or kind=='INCOME' or kind=='CASH_FLOW' or kind=='NOTES'?'':'hidden'}><input type="checkbox" checked data-fs-comparison /><spring:message code="fs.showComparison" /></label>
  <p class="fs-help"><spring:message code="fs.previewSettingsHelp" /></p></div></details>
 </div>
 <%@ include file="preview-report.jspf" %>
 <div class="fs-preview-footer">
 <c:if test="${not run.saved}"><form class="fs-save-report" data-fs-save method="post" action="<c:url value='${base}/save' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="kind" value="${kind}" />
 <input type="hidden" name="selectionKind" value="<c:out value='${selectionKind}' />" />
 <input type="hidden" name="line" value="<c:out value='${line}' />" />
 <input type="hidden" name="account" value="<c:out value='${account}' />" />
 <input type="hidden" name="page" value="<c:out value='${page}' />" />
 <button class="app-btn btn-launch"><spring:message code="common.savedraft" /></button></form></c:if>
 </div>
</div>
</div>
<%@ include file="../../fragments/footer.jspf" %>
