<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/report-builder.css' />" />
<div class="report-builder">
  <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="report.title" /></h1></div>
  <p><spring:message code="report.boundary" /></p>
  <c:if test="${not empty reportError}"><p role="alert" class="report-error"><spring:message code="${reportError}" /></p></c:if>
  <c:if test="${not empty reportSuccess}"><p role="status"><spring:message code="${reportSuccess}" /></p></c:if>
  <textarea id="report-seed" hidden><c:out value="${definitionJson}" /></textarea>
  <textarea id="report-catalog" hidden><c:out value="${catalogJson}" /></textarea>
  <div id="report-labels" hidden>
    <c:forEach items="${fieldLabels}" var="label"><span data-field="<c:out value='${label.key}' />" data-description="<c:out value='${fieldDescriptions[label.key]}' />"><c:out value="${label.value}" /></span></c:forEach>
  </div>
  <div id="report-words" hidden>
    <span data-word="show"><spring:message code="report.show" /></span><span data-word="alias"><spring:message code="report.alias" /></span>
    <span data-word="width"><spring:message code="report.width" /></span><span data-word="total"><spring:message code="report.total" /></span>
    <span data-word="group"><spring:message code="report.group" /></span><span data-word="up"><spring:message code="report.up" /></span>
    <span data-word="down"><spring:message code="report.down" /></span><span data-word="remove"><spring:message code="report.remove" /></span>
    <span data-word="field"><spring:message code="report.field" /></span><span data-word="operator"><spring:message code="report.operator" /></span><span data-word="value"><spring:message code="report.value" /></span>
    <span data-word="EQ"><spring:message code="report.EQ" /></span><span data-word="GE"><spring:message code="report.GE" /></span><span data-word="LE"><spring:message code="report.LE" /></span>
  </div>
  <sec:authorize access="@access.has(principal,'REPORT_TEMPLATE_DESIGN')">
  <c:url var="saveUrl" value="/reports/builder/templates" /><c:url var="previewUrl" value="/reports/builder/preview" />
  <form id="report-designer" method="post" action="${saveUrl}" class="report-panel">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    <input type="hidden" id="report-definition" name="definition" />
    <div class="report-controls">
      <label><spring:message code="report.dataset" /><select class="aws-control" id="report-dataset"><c:forEach items="${catalog}" var="data"><option value="${data.dataset}"><spring:message code="report.dataset.${data.dataset}" /></option></c:forEach></select></label>
      <label><spring:message code="report.name" /><input class="aws-control" id="report-name" maxlength="100" required /></label>
      <label><spring:message code="report.language" /><select class="aws-control" id="report-language"><option value="en">English</option><option value="sw">Kiswahili</option></select></label>
      <label><spring:message code="report.orientation" /><select class="aws-control" id="report-landscape"><option value="false"><spring:message code="report.portrait" /></option><option value="true"><spring:message code="report.landscape" /></option></select></label>
      <label><spring:message code="report.footer" /><input class="aws-control" id="report-footer" maxlength="160" /></label>
      <label><spring:message code="report.logo" /><input type="checkbox" id="report-logo" /></label>
    </div>
    <p id="report-source-note"><spring:message code="report.catalogNote" /></p>
    <h2><spring:message code="report.columns" /></h2>
    <p><spring:message code="report.columnHelp" /></p>
    <div id="report-columns"></div>
    <div class="report-controls">
      <label><spring:message code="report.sort" /><select class="aws-control" id="report-sort"></select></label>
      <label><spring:message code="report.direction" /><select class="aws-control" id="report-descending"><option value="false"><spring:message code="report.ascending" /></option><option value="true"><spring:message code="report.descending" /></option></select></label>
    </div>
    <h2><spring:message code="report.filters" /></h2><div id="report-filters"></div>
    <button type="button" class="app-btn btn-neutral" id="report-add-filter"><spring:message code="report.addFilter" /></button>
    <div class="report-controls">
      <label><spring:message code="report.from" /><input type="date" name="from" value="<c:out value='${from}' />" required class="aws-control" /></label>
      <label><spring:message code="report.through" /><input type="date" name="through" value="<c:out value='${through}' />" required class="aws-control" /></label>
      <c:if test="${not empty sourceTemplate and sourceTemplate.madeBy == reportActorId}"><label><input type="checkbox" name="templateId" value="${sourceTemplate.templateId}" /><spring:message code="report.newVersion" /></label></c:if>
      <sec:authorize access="@access.has(principal,'REPORT_TEMPLATE_SHARE')"><label><input type="checkbox" name="shared" value="true" /><spring:message code="report.shared" /></label></sec:authorize>
    </div>
    <div class="report-actions"><button type="submit" class="app-btn btn-primary"><spring:message code="report.saveDraft" /></button>
      <sec:authorize access="@access.has(principal,'REPORT_RUN')"><button type="submit" class="app-btn btn-neutral" formaction="${previewUrl}"><spring:message code="report.preview" /></button></sec:authorize>
    </div>
  </form>
  </sec:authorize>
  <c:if test="${not empty result}">
    <section class="erp-table-wrap">
      <div class="app-table-titlebar"><h2><c:out value="${result.definition.title}" /></h2><span><c:out value="${result.rowsInScope}" /> <spring:message code="report.rows" /></span></div>
      <div class="report-context"><p><c:out value="${reportCoverage}" /></p>
        <p><spring:message code="report.scope" />: <c:out value="${result.institution}" /> / <c:out value="${result.branch}" /></p>
        <p><spring:message code="report.filters" />: <c:choose><c:when test="${empty result.definition.filters}"><spring:message code="report.noFilters" /></c:when><c:otherwise><c:forEach items="${result.definition.filters}" var="filter"><c:out value="${fieldLabels[filter.field.name()]}" /> <spring:message code="report.${filter.operator}" /> <c:out value="${filter.value}" />; </c:forEach></c:otherwise></c:choose></p>
        <p><spring:message code="report.period" />: <c:out value="${result.from}" /> / <c:out value="${result.through}" />. <spring:message code="report.cutoff" />: <c:out value="${result.recordedCutoff}" /></p>
        <p><spring:message code="report.untracked" />: <c:out value="${result.untrackedLoans}" /></p></div>
      <div class="erp-table-scroll" data-view-position-key="operational-report-preview"><table class="erp-table"><thead><tr>
        <c:forEach items="${result.visibleColumns}" var="column"><th style="min-width:${column.width}px"><c:if test="${not empty column.label}"><c:out value="${column.label}" /> / </c:if><c:out value="${fieldLabels[column.field.name()]}" /><c:if test="${column.field.unit == 'TZS'}"> (TZS)</c:if></th></c:forEach>
      </tr></thead><tbody><c:forEach items="${result.rows}" var="row"><tr><c:forEach items="${result.visibleColumns}" var="column"><td class="${column.field.unit == 'TZS' ? 'report-money' : ''}">
        <c:choose><c:when test="${row[column.field.name()] == null}"><spring:message code="report.unknown" /></c:when><c:when test="${column.field.unit == 'TZS'}"><fmt:formatNumber value="${row[column.field.name()]}" minFractionDigits="2" maxFractionDigits="2" /></c:when><c:otherwise><c:out value="${row[column.field.name()]}" /></c:otherwise></c:choose>
      </td></c:forEach></tr></c:forEach><c:if test="${empty result.rows}"><tr><td colspan="${result.visibleColumns.size()}"><spring:message code="report.empty" /></td></tr></c:if></tbody></table></div>
      <div class="report-context"><strong><spring:message code="report.scopeTotals" /></strong><c:forEach items="${result.totals}" var="total"><p><c:out value="${fieldLabels[total.key]}" /> (TZS): <fmt:formatNumber value="${total.value}" minFractionDigits="2" maxFractionDigits="2" /></p></c:forEach></div>
      <div class="report-actions">
        <c:if test="${not empty runVersion}">
          <c:url var="runUrl" value="/reports/builder/templates/${runVersion}/run" />
          <form method="get" action="${runUrl}"><input type="hidden" name="from" value="${result.from}" /><input type="hidden" name="through" value="${result.through}" /><input type="hidden" name="cutoff" value="${result.recordedCutoff}" />
            <c:if test="${result.page > 0}"><button class="app-btn btn-neutral" name="page" value="${result.page-1}"><spring:message code="common.previous" /></button></c:if><c:if test="${result.truncated}"><button class="app-btn btn-neutral" name="page" value="${result.page+1}"><spring:message code="common.next" /></button></c:if>
          </form>
        </c:if>
        <c:if test="${empty runVersion}">
          <form method="post" action="${previewUrl}"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="definition" value="<c:out value='${definitionJson}' />" /><input type="hidden" name="from" value="${result.from}" /><input type="hidden" name="through" value="${result.through}" /><input type="hidden" name="cutoff" value="${result.recordedCutoff}" />
            <c:if test="${result.page > 0}"><button class="app-btn btn-neutral" name="page" value="${result.page-1}"><spring:message code="common.previous" /></button></c:if><c:if test="${result.truncated}"><button class="app-btn btn-neutral" name="page" value="${result.page+1}"><spring:message code="common.next" /></button></c:if>
          </form>
        </c:if>
        <sec:authorize access="@access.has(principal,'REPORT_EXPORT')">
          <c:choose><c:when test="${not empty runVersion}"><c:url var="exportUrl" value="/reports/builder/templates/${runVersion}/export" /></c:when><c:otherwise><c:url var="exportUrl" value="/reports/builder/preview/export" /></c:otherwise></c:choose>
          <form action="${exportUrl}" method="${not empty runVersion ? 'get' : 'post'}"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="definition" value="<c:out value='${definitionJson}' />" /><input type="hidden" name="from" value="${result.from}" /><input type="hidden" name="through" value="${result.through}" /><input type="hidden" name="cutoff" value="${result.recordedCutoff}" />
          <c:forEach items="${['CSV','XLSX','PDF']}" var="format"><button class="app-btn btn-neutral" name="format" value="${format}"><c:out value="${format}" /></button></c:forEach></form>
        </sec:authorize>
      </div>
      <c:if test="${result.rowsInScope > 2000}"><p class="report-context"><spring:message code="report.error.largeExport" /></p></c:if>
    </section>
  </c:if>
  <section class="erp-table-wrap"><div class="app-table-titlebar"><h2><spring:message code="report.templates" /></h2></div>
    <div class="erp-table-scroll" data-view-position-key="operational-report-templates"><table class="erp-table"><thead><tr><th><spring:message code="report.name" /></th><th><spring:message code="report.version" /></th><th><spring:message code="report.state" /></th><th><spring:message code="common.actions" /></th></tr></thead><tbody>
      <c:forEach items="${templates}" var="template"><tr><td><c:out value="${template.definition.title}" /></td><td><c:out value="${template.version}" /></td><td><spring:message code="report.state.${template.state}" /></td><td class="report-template-actions">
        <sec:authorize access="@access.has(principal,'REPORT_TEMPLATE_DESIGN')"><a class="app-btn btn-neutral" href="<c:url value='/reports/builder?version=${template.id}' />"><spring:message code="report.clone" /></a></sec:authorize>
        <c:if test="${template.state == 'DRAFT'}"><sec:authorize access="@access.has(principal,'REPORT_TEMPLATE_PUBLISH')"><form method="post" action="<c:url value='/reports/builder/templates/${template.id}/publish' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><button class="app-btn btn-neutral"><spring:message code="report.publish" /></button></form></sec:authorize></c:if>
        <c:if test="${template.state == 'PUBLISHED'}">
          <sec:authorize access="@access.has(principal,'REPORT_RUN')"><form method="get" action="<c:url value='/reports/builder/templates/${template.id}/run' />"><label><spring:message code="report.from" /><input type="date" name="from" value="${from}" required class="aws-control" /></label><label><spring:message code="report.through" /><input type="date" name="through" value="${through}" required class="aws-control" /></label><button class="app-btn btn-neutral"><spring:message code="report.run" /></button></form></sec:authorize>
          <sec:authorize access="@access.has(principal,'REPORT_TEMPLATE_PUBLISH')"><form method="post" action="<c:url value='/reports/builder/templates/${template.id}/retire' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><button class="app-btn btn-neutral"><spring:message code="report.retire" /></button></form></sec:authorize>
        </c:if>
      </td></tr></c:forEach><c:if test="${empty templates}"><tr><td colspan="4"><spring:message code="report.noTemplates" /></td></tr></c:if>
    </tbody></table></div>
    <div class="report-actions"><c:if test="${templatePage > 0}"><a class="app-btn btn-neutral" href="<c:url value='/reports/builder?page=${templatePage-1}' />"><spring:message code="common.previous" /></a></c:if><c:if test="${templates.size() == 25}"><a class="app-btn btn-neutral" href="<c:url value='/reports/builder?page=${templatePage+1}' />"><spring:message code="common.next" /></a></c:if></div>
  </section>
</div>
<script src="<c:url value='/js/report-builder.js' />" defer></script>
<%@ include file="../fragments/footer.jspf" %>
